"""Tests for run-device-tests.py argument handling. Run: python3 -m unittest discover scripts"""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location(
    'run_device_tests', Path(__file__).with_name('run-device-tests.py')
)
run_device_tests = importlib.util.module_from_spec(spec)
spec.loader.exec_module(run_device_tests)

PREFIX = '-Pandroid.testInstrumentationRunnerArguments.'


class RunnerArgumentsTest(unittest.TestCase):
    def test_no_filter_runs_everything(self):
        self.assertEqual(run_device_tests.runner_arguments([], None), [])

    def test_features_accept_name_with_or_without_extension_and_path(self):
        arguments = run_device_tests.runner_arguments(
            ['free_drawing.feature', 'mode_select', 'app/src/androidTest/assets/features/free_drawing.feature'],
            None
        )
        self.assertEqual(
            arguments,
            [f'{PREFIX}features=features/free_drawing.feature,features/mode_select.feature']
        )

    def test_unknown_feature_fails_before_running(self):
        with self.assertRaises(ValueError):
            run_device_tests.runner_arguments(['no_such_feature.feature'], None)

    def test_tag_expression_is_passed_unquoted(self):
        self.assertEqual(
            run_device_tests.runner_arguments([], '@smoke and not @slow'),
            [f'{PREFIX}tags=@smoke and not @slow']
        )


if __name__ == '__main__':
    unittest.main()
