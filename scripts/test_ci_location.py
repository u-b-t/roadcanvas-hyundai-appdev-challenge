import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts import ci_location as guard


class LocationGuardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.state_file = Path(self.tmp.name) / 'state.json'
        self.state = 'true'
        self.calls = []
        for item in (
            patch.object(guard, 'STATE_FILE', self.state_file),
            patch.dict(guard.os.environ, {'ANDROID_SERIAL': 'emulator-5554'}),
            patch.object(guard, 'adb', self.adb),
        ):
            item.start()
            self.addCleanup(item.stop)

    def adb(self, *args):
        if args[:2] == ('shell', 'pidof'):
            return '123'
        if args[3] == 'is-location-enabled':
            return self.state
        self.state = args[4]
        self.calls.append(self.state)
        return ''

    def test_success_restores_enabled_location(self):
        with patch.object(guard.subprocess, 'run') as command:
            command.return_value.returncode = 0
            self.assertEqual(guard.run(['tests']), 0)
        self.assertEqual(self.calls, ['false', 'true'])
        self.assertFalse(self.state_file.exists())

    def test_failure_keeps_previously_disabled_location(self):
        self.state = 'false'
        with patch.object(guard.subprocess, 'run') as command:
            command.return_value.returncode = 42
            self.assertEqual(guard.run(['tests']), 42)
        self.assertEqual(self.state, 'false')
        self.assertFalse(self.state_file.exists())

    def test_interrupted_run_is_restored_before_next_run(self):
        self.state = 'false'
        self.state_file.write_text(json.dumps({'serial': 'emulator-5554', 'state': 'true'}))
        with patch.object(guard.subprocess, 'run') as command:
            command.return_value.returncode = 0
            self.assertEqual(guard.run(['tests']), 0)
        self.assertEqual(self.calls, ['true', 'false', 'true'])

    def test_other_device_does_not_change_settings(self):
        self.state_file.write_text(json.dumps({'serial': 'emulator-5556', 'state': 'true'}))
        with self.assertRaises(RuntimeError):
            guard.run(['tests'])
        self.assertEqual(self.calls, [])
        self.assertTrue(self.state_file.exists())

    def test_interruption_restores_original_setting(self):
        with patch.object(guard.subprocess, 'run', side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt):
                guard.run(['tests'])
        self.assertFalse(self.state_file.exists())

    def test_system_server_restart_fails_and_restores_location(self):
        pids = iter(['123', '456'])
        with patch.object(guard, 'system_server_pid', side_effect=lambda: next(pids)):
            with patch.object(guard.subprocess, 'run') as command:
                command.return_value.returncode = 0
                with self.assertRaisesRegex(RuntimeError, 'system_server'):
                    guard.run(['tests'])
        self.assertEqual(self.state, 'true')
        self.assertFalse(self.state_file.exists())

    def test_restore_failure_keeps_recovery_state(self):
        self.state_file.write_text(json.dumps({'serial': 'emulator-5554', 'state': 'true'}))
        with patch.object(guard, 'adb', side_effect=OSError('unavailable')):
            with self.assertRaises(OSError):
                guard.restore()
        self.assertTrue(self.state_file.exists())


if __name__ == '__main__':
    unittest.main()
