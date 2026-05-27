import argparse
import os
import re
import subprocess
import sys
import textwrap

ignore_charts = []

process_success = []
process_failed = []
chart_errors = {
    'missing_key': {},
    'non_existing_key': {}
}

# import logging
# class Logger(logging.Logger):

#     def __init__(self, name: str) -> None:

#         super().__init__(name)
#         self.setLevel(logging.DEBUG)
#         self.addLoggingLevel('SUCCESS', 25, 'success')
#         self.addHandler(LoggerConsoleHandler())

#     def addLoggingLevel(self,levelName: str, levelNum: int, methodName: str):

#         def logForLevel(self, message, *args, **kwargs):
#             if self.isEnabledFor(levelNum):
#                 self._log(levelNum, message, args, **kwargs)

#         def logToRoot(message, *args, **kwargs):
#             logging.log(levelNum, message, *args, **kwargs)

#         logging.addLevelName(levelNum, levelName)
#         setattr(logging, levelName, levelNum)
#         setattr(logging.getLoggerClass(), methodName, logForLevel)
#         setattr(logging, methodName, logToRoot)

# class LoggerConsoleHandler(logging.StreamHandler):

#     def __init__(self, level: int = logging.DEBUG) -> None:

#         super().__init__()
#         self.setFormatter(LoggerFormatter())
#         self.setLevel(level)

# class LoggerFormatter(logging.Formatter):

#     def format(self, record):

#         self.FORMATS = {
#             logging.DEBUG: '\033[90m' + '%(message)s' + '\033[0m',
#             logging.INFO: '\033[94m' + '%(message)s' + '\033[0m',
#             logging.SUCCESS: '\033[92m' + '%(message)s' + '\033[0m',
#             logging.WARNING: '\033[93m' + '%(message)s' + '\033[0m',
#             logging.ERROR: '\033[91m' + '%(message)s' + '\033[0m',
#             logging.CRITICAL: '\033[41m' + '%(message)s' + '\033[0m'
#         }

#         log_fmt = self.FORMATS.get(record.levelno)
#         formatter = logging.Formatter(log_fmt)
#         return formatter.format(record)

# logging.setLoggerClass(Logger)
# logger = logging.getLogger(__name__)
# logger.debug('DEBUG')
# logger.info('INFO')
# logger.success('SUCCESS')
# logger.warning('WARNING')
# logger.error('ERROR')
# logger.critical('CRITICAL')
# sys.exit(0)


class Colours:
    '''Define colours for console output.'''
    RED = '\033[91m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    NC = '\033[0m'


class Console:
    '''Define console output methods for different message types.'''

    @classmethod
    def print(self, message: str, colour: Colours=Colours.NC, indent: int=0, end: str='\n') -> None:
        '''Prints a message to the console.

        @param message The info message to print.
        @param colour The colour to print the message in.
        @param indent The number of spaces to indent the message by.
        @param end The string to append to the end of the printed message.
        '''
        indent_str = ' ' * indent
        print(textwrap.indent(f'{colour}{message}{Colours.NC}', indent_str), end=end)

    @classmethod
    def info(self, message: str, **kwargs) -> None:
        '''Prints a info message to the console. Extends `print`.'''
        self.print(f'{message}', Colours.BLUE, **kwargs)

    @classmethod
    def success(self, message: str, **kwargs) -> None:
        '''Prints a success message to the console. Extends `print`.'''
        self.print(f'{message}', Colours.GREEN, **kwargs)

    @classmethod
    def warning(self, message: str, **kwargs) -> None:
        '''Prints a warning message to the console. Extends `print`.'''
        self.print(f'{message}', Colours.YELLOW, **kwargs)

    @classmethod
    def error(self, message: str, **kwargs) -> None:
        '''Prints an error message to the console. Extends `print`.'''
        self.print(f'{message}', Colours.RED, **kwargs)


def get_charts() -> dict:
    '''Returns a dictionary of all chart name -> chart path for all charts in
    the charts directory, excluding those in the `ignore_charts` list.
    '''

    charts = {}
    for root, _, files in os.walk('charts'):
        if 'Chart.yaml' in files and not any(ignore_chart in root for ignore_chart in ignore_charts):
            name = os.path.basename(root)
            charts[name] = root
    return charts


def process_chart(chart_name: str, chart_path: str) -> bool:
    '''Processes a singe chart, returning `True` if the chart metadata is valid,
    otherwise `False`.

    @param chart_name The name of the chart to process.
    @param chart_path The path to the chart to process.
    '''

    Console.info(f'\nProcessing chart: {chart_name}')

    # Test that the path exists.
    is_dir = os.path.isdir(chart_path)
    if not is_dir:
        return False

    # Set paths to files required by the readme generator.
    readme_config_file = os.path.join(chart_path, 'readme.config')
    readme_file = os.path.join(chart_path, 'README.md')
    values_file = os.path.join(chart_path, 'values.yaml')
    values_schema_file = os.path.join(chart_path, 'values.schema.json')

    # Test that a values.yaml file exists. If not, fail this chart.
    if not os.path.exists(values_file):
        Console.error('values.yaml file does not exist in chart.')

    # Test that a readme.config file exists. If not, fail this chart.
    if not os.path.exists(readme_config_file):
        Console.error('readme.config file does not exist in chart.')

    # Test that a README.md file exists. If not, create it.
    if not os.path.exists(readme_file):
        with open(readme_file, 'w') as f:
            Console.warning('README.md file does not exist in chart, so will be created.')
            f.write(f'# {chart_name}\n\n ## Parameters')

    # Run the metadata generator for this chart.
    cmd = (f'{readme_generator_cmd} '
        f'--config {readme_config_file} '
        f'--readme {readme_file} '
        f'--values {values_file} '
        f'--schema {values_schema_file}')
    process = subprocess.Popen(cmd.split(), stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    output, _ = process.communicate()

    # Check the output for success or failure.
    if process.returncode == 0:
        Console.success(f'  ✓ Completed {chart_name}')
        process_success.append(chart_name)
    else:
        Console.error(f'  ✗ Failed to process {chart_name} (exit code: {process.returncode})')
        Console.print(output.decode(), indent=4)
        process_failed.append(chart_name)

        missing_key_pattern = r'.*missing metadata for key.*'
        missing_key_errors = re.findall(missing_key_pattern, output.decode(), re.IGNORECASE | re.MULTILINE)
        chart_errors['missing_key'][chart_name] = missing_key_errors

        non_existing_key_pattern = r'.*metadata provided for non existing key.*'
        non_existing_key_errors = re.findall(non_existing_key_pattern, output.decode(), re.IGNORECASE | re.MULTILINE)
        chart_errors['non_existing_key'][chart_name] = non_existing_key_errors

        if missing_key_errors or non_existing_key_errors:
            Console.warning('    Metadata misconfiguration detected.')
            Console.warning('     -- Ensure all configuration values have @key annotations.')
            Console.warning('     -- Check values.yaml for proper documentation format.')
            Console.warning('     -- Verify readme.config contains all required sections.')

    return False if chart_errors['missing_key'] or chart_errors['non_existing_key'] else True


def print_summary() -> None:
    '''Prints a summary of the metadata update process, including the number of
    charts processed successfully and unsuccessfully. Also lists the key erros
    detected for failed charts.'''

    Console.info('\n\nProcessing Summary')
    Console.info('-' * 18)

    # Output any charts that were successfully processed.
    if process_success:
        Console.success(f'\n{len(process_success)} chart(s) processed successfully -')
        for chart in process_success:
            Console.success('  ✓', end=' ')
            Console.print(f'{chart}')

    # Output any charts that failed to process.
    if process_failed:
        Console.error(f'\n{len(process_failed)} chart(s) failed to process -')
        for chart in process_failed:
            Console.error('  ✗', end=' ')
            Console.print(f'{chart}')
        Console.warning('\nCommon solutions for metadata key errors -')
        Console.warning('  1. Add @key annotations to all configuration values in values.yaml')
        Console.warning('  2. Ensure @section and @descStart/@descEnd are properly formatted')
        Console.warning('  3. Check that readme.config includes all required sections')
        Console.warning('  4. Verify values.yaml syntax is valid YAML')
    else:
        Console.success('\nAll charts processed successfully!')

    # List specific chart errors by type.
    if chart_errors:
        for error_type, charts in chart_errors.items():
            if charts:
                Console.error(f'\nSummary of {error_type.title().replace('_', ' ')} Errors')
                for chart, errors in charts.items():
                    Console.error('  ●', end=' ')
                    Console.info(f'{chart}:', end=' ')
                    for error in errors:
                        Console.print(error[error.rindex(':')+1:].strip())


if __name__ == '__main__':
    # parse command line arguments
    parser = argparse.ArgumentParser(description='Run metadata updates for all charts.')
    parser.add_argument('--ci', action='store_true', default=False,
        help='Indicate execution is happening in a CI environment.')
    args = parser.parse_args()

    # Test to see if the script is being run in CI mode, and set the readme generator command accordingly.
    global readme_generator_cmd
    if args.ci:
        readme_generator_cmd = 'npx @bitnami/readme-generator-for-helm'
    else:
        readme_generator_cmd = '.dev/readme-generator-for-helm'

    # CI flag currently isn't used by the script logic, but it's available
    # for callers who may want to change behaviour in the future.
    ci_mode = args.ci

    Console.info('\nStarting metadata update for all charts')
    Console.info('-' * 39)

    charts = get_charts()
    results = []
    for chart_name, chart_path in charts.items():
        results.append(process_chart(chart_name, chart_path))

    print_summary()
    if all(results):
        sys.exit(0)
    else:
        sys.exit(1)
