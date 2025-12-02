#!/usr/bin/env python3
"""
Notification Service - Test Runner
===================================

CLI для запуска тестов с различными опциями.

Использование:
    python run_tests.py                     # Все тесты
    python run_tests.py --smoke             # Только smoke тесты
    python run_tests.py --coverage          # С покрытием
    python run_tests.py --parallel          # Параллельно
    python run_tests.py --report            # С HTML отчётом
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(
        description="Notification Service Test Runner",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
    python run_tests.py                     # Run all tests
    python run_tests.py --smoke             # Run smoke tests only
    python run_tests.py --api               # Run API tests only
    python run_tests.py --security          # Run security tests only
    python run_tests.py --performance       # Run performance tests
    python run_tests.py --coverage          # Run with coverage report
    python run_tests.py --parallel -n 4     # Run in parallel with 4 workers
    python run_tests.py --report            # Generate HTML report
    python run_tests.py --allure            # Generate Allure report
        """
    )
    
    # Test selection
    parser.add_argument("--smoke", action="store_true", help="Run smoke tests only")
    parser.add_argument("--api", action="store_true", help="Run API tests only")
    parser.add_argument("--auth", action="store_true", help="Run auth tests only")
    parser.add_argument("--notifications", action="store_true", help="Run notification tests")
    parser.add_argument("--security", action="store_true", help="Run security tests")
    parser.add_argument("--performance", action="store_true", help="Run performance tests")
    parser.add_argument("--integration", action="store_true", help="Run integration tests")
    parser.add_argument("--e2e", action="store_true", help="Run end-to-end tests")
    parser.add_argument("-m", "--marker", help="Custom pytest marker")
    parser.add_argument("-k", "--keyword", help="Filter by keyword expression")
    
    # Execution options
    parser.add_argument("--parallel", action="store_true", help="Run tests in parallel")
    parser.add_argument("-n", "--workers", type=int, default=4, help="Number of parallel workers")
    parser.add_argument("--coverage", action="store_true", help="Generate coverage report")
    parser.add_argument("--report", action="store_true", help="Generate HTML report")
    parser.add_argument("--allure", action="store_true", help="Generate Allure report")
    parser.add_argument("--verbose", "-v", action="count", default=0, help="Verbosity level")
    parser.add_argument("--failed", action="store_true", help="Run only previously failed tests")
    parser.add_argument("--last-failed", action="store_true", help="Run last failed tests first")
    
    # Environment
    parser.add_argument("--env", choices=["local", "docker", "staging"], 
                       default="docker", help="Test environment")
    parser.add_argument("--api-url", help="Override API URL")
    
    args = parser.parse_args()
    
    # Build pytest command
    cmd = ["pytest"]
    
    # Test selection
    markers = []
    if args.smoke:
        markers.append("smoke")
    if args.api:
        markers.append("api")
    if args.auth:
        markers.append("auth")
    if args.notifications:
        markers.append("notifications")
    if args.security:
        markers.append("security")
    if args.performance:
        markers.append("performance")
    if args.integration:
        markers.append("integration")
    if args.e2e:
        markers.append("e2e")
    if args.marker:
        markers.append(args.marker)
    
    if markers:
        cmd.extend(["-m", " or ".join(markers)])
    
    if args.keyword:
        cmd.extend(["-k", args.keyword])
    
    # Execution options
    if args.parallel:
        cmd.extend(["-n", str(args.workers)])
    
    if args.coverage:
        cmd.extend([
            "--cov=.",
            "--cov-report=term-missing",
            "--cov-report=html:reports/coverage",
            "--cov-fail-under=80"
        ])
    
    if args.report:
        cmd.extend(["--html=reports/test_report.html", "--self-contained-html"])
    
    if args.allure:
        cmd.extend(["--alluredir=reports/allure"])
    
    # Verbosity
    if args.verbose:
        cmd.append("-" + "v" * args.verbose)
    
    if args.failed:
        cmd.append("--lf")
    elif args.last_failed:
        cmd.append("--ff")
    
    # Add test directory
    cmd.append("tests/")
    
    # Set environment variables
    env = os.environ.copy()
    env["TEST_ENV"] = args.env
    
    if args.api_url:
        env["TEST_API_URL"] = args.api_url
    
    # Print command
    print(f"\n{'='*60}")
    print("Notification Service Test Runner")
    print(f"{'='*60}")
    print(f"Command: {' '.join(cmd)}")
    print(f"Environment: {args.env}")
    print(f"{'='*60}\n")
    
    # Run tests
    result = subprocess.run(cmd, env=env, cwd=Path(__file__).parent)
    
    # Print summary
    print(f"\n{'='*60}")
    if result.returncode == 0:
        print("✅ All tests passed!")
    else:
        print(f"❌ Tests failed with exit code: {result.returncode}")
    print(f"{'='*60}\n")
    
    # Open reports if generated
    if args.report and result.returncode == 0:
        report_path = Path(__file__).parent / "reports" / "test_report.html"
        if report_path.exists():
            print(f"📊 HTML Report: {report_path}")
    
    if args.coverage and result.returncode == 0:
        coverage_path = Path(__file__).parent / "reports" / "coverage" / "index.html"
        if coverage_path.exists():
            print(f"📈 Coverage Report: {coverage_path}")
    
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
