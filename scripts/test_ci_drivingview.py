import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import ci_drivingview as guard


class DrivingViewGuardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.state_file = Path(self.tmp.name) / 'state.json'
        self.state = '0'
        self.calls = []
        self.patches = [
            patch.object(guard, 'STATE_FILE', self.state_file),
            patch.dict(guard.os.environ, {'ANDROID_SERIAL': 'emulator-5554'}),
            patch.object(guard, 'adb', self.adb),
        ]
        for item in self.patches:
            item.start()
            self.addCleanup(item.stop)

    def adb(self, *args):
        if args[:3] == ('shell', 'dumpsys', 'package'):
            return f'    User 10: installed=true enabled={self.state} instant=false\n'
        operation = args[2]
        self.calls.append(operation)
        self.state = {value: key for key, value in guard.COMMANDS.items()}[operation]
        return 'ok'

    def test_success_restores_original_state(self):
        with patch.object(guard.subprocess, 'run') as command:
            command.return_value.returncode = 0
            self.assertEqual(guard.run(['tests']), 0)
        self.assertEqual(self.calls, ['disable-user', 'default-state'])
        self.assertFalse(self.state_file.exists())

    def test_failed_test_still_restores_original_enabled_state(self):
        self.state = '1'
        with patch.object(guard.subprocess, 'run') as command:
            command.return_value.returncode = 42
            self.assertEqual(guard.run(['tests']), 42)
        self.assertEqual(self.state, '1')
        self.assertFalse(self.state_file.exists())

    def test_previous_interrupted_run_is_restored_before_next_run(self):
        self.state = '3'
        self.state_file.write_text(json.dumps({'serial': 'emulator-5554', 'state': '0'}))
        with patch.object(guard.subprocess, 'run') as command:
            command.return_value.returncode = 0
            self.assertEqual(guard.run(['tests']), 0)
        self.assertEqual(self.calls, ['default-state', 'disable-user', 'default-state'])

    def test_refuses_state_saved_for_another_device(self):
        self.state_file.write_text(json.dumps({'serial': 'emulator-5556', 'state': '0'}))
        with self.assertRaises(RuntimeError):
            guard.run(['tests'])
        self.assertEqual(self.calls, [])
        self.assertTrue(self.state_file.exists())

    def test_interruption_restores_original_setting(self):
        with patch.object(guard.subprocess, 'run', side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt):
                guard.run(['tests'])
        self.assertFalse(self.state_file.exists())

    def test_restore_failure_keeps_saved_state(self):
        self.state_file.write_text(json.dumps({'serial': 'emulator-5554', 'state': '0'}))
        with patch.object(guard, 'adb', side_effect=OSError('unavailable')):
            with self.assertRaises(OSError):
                guard.restore()
        self.assertTrue(self.state_file.exists())


if __name__ == '__main__':
    unittest.main()
