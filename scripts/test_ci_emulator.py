"""Exercise the CI emulator preparation decisions without starting an emulator."""
import subprocess
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name('ci-emulator.sh').read_text()
FUNCTIONS = SCRIPT[:SCRIPT.index('\ncommand=${1:-}')]


def prepare_scenario(processes, devices, active_avd='Pleos'):
    setup = f'''
avd=Pleos
adb=mock_adb
with_timeout() {{ shift; "$@"; }}
mock_adb() {{ printf '%s\\n' 'List of devices attached' {devices!r}; }}
adb_device() {{ printf '%s\\n' {active_avd!r}; }}
emulator_pids() {{ printf '%s' {processes!r}; }}
stop_emulator() {{ echo STOP; }}
cold_boot() {{ echo BOOT; }}
prepare
'''
    return subprocess.run(['bash', '-c', FUNCTIONS + setup], capture_output=True, text=True, errors='replace')


class PrepareTests(unittest.TestCase):
    def test_running_pleos_is_stopped_before_cold_boot(self):
        result = prepare_scenario('123', 'emulator-5554 device')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertLess(result.stdout.index('STOP'), result.stdout.index('BOOT'))

    def test_absent_emulator_cold_boots(self):
        result = prepare_scenario('', '')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn('STOP', result.stdout)
        self.assertIn('BOOT', result.stdout)

    def test_other_emulator_is_not_stopped(self):
        result = prepare_scenario('123', 'emulator-5556 device')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('STOP', result.stdout)
        self.assertNotIn('BOOT', result.stdout)

    def test_other_avd_on_target_port_is_not_stopped(self):
        result = prepare_scenario('123', 'emulator-5554 device', 'Other')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('STOP', result.stdout)
        self.assertNotIn('BOOT', result.stdout)


if __name__ == '__main__':
    unittest.main()
