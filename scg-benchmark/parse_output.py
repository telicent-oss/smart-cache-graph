import re
import sys

def parse_jmh_output(file_path):
    """Parses JMH output from a file and returns a dictionary of results."""

    results = {}

    try:
        with open(file_path, 'r') as f:
            output = f.read()

        # Extract benchmark name
        benchmark_match = re.search(r'Benchmark: ([\w.]+)', output)
        if benchmark_match:
            results['benchmark'] = benchmark_match.group(1)

        # Extract score and error
        score_match = re.search(r'([\d.]+) ±\(([\d.]+)%\) ([\d.]+) ms/op', output)
        if score_match:
            results['score'] = float(score_match.group(1))
            results['error_percent'] = float(score_match.group(2))
            results['error'] = float(score_match.group(3))

        # Extract min, avg, max
        min_avg_max_match = re.search(r'\(min, avg, max\) = \(([\d.]+), ([\d.]+), ([\d.]+)\)', output)
        if min_avg_max_match:
            results['min'] = float(min_avg_max_match.group(1))
            results['avg'] = float(min_avg_max_match.group(2))
            results['max'] = float(min_avg_max_match.group(3))

        return results

    except FileNotFoundError:
        print(f"Error: File not found: {file_path}")
        return None
    except Exception as e:
        print(f"An error occurred: {e}")
        return None

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python parse_jmh.py <jmh_output_file>")
    else:
        file_path = sys.argv[1]
        parsed_results = parse_jmh_output(file_path)
        if parsed_results:
            print(parsed_results)