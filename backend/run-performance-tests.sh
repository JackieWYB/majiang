#!/bin/bash

# Performance Test Runner Script for Mahjong Game Backend
# This script runs comprehensive performance tests and generates reports

set -e

echo "=== Mahjong Game Performance Test Runner ==="
echo "Starting performance tests at $(date)"

# Configuration
TEST_PROFILE="performance"
MAVEN_OPTS="-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
REDIS_PORT=6379
MYSQL_PORT=3306

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if service is running
check_service() {
    local service=$1
    local port=$2
    
    if nc -z localhost $port 2>/dev/null; then
        print_status "$service is running on port $port"
        return 0
    else
        print_error "$service is not running on port $port"
        return 1
    fi
}

# Function to start Redis if not running
start_redis() {
    if ! check_service "Redis" $REDIS_PORT; then
        print_status "Starting Redis server..."
        redis-server --daemonize yes --port $REDIS_PORT
        sleep 2
        if check_service "Redis" $REDIS_PORT; then
            print_status "Redis started successfully"
        else
            print_error "Failed to start Redis"
            exit 1
        fi
    fi
}

# Function to cleanup
cleanup() {
    print_status "Cleaning up..."
    # Kill any background processes
    jobs -p | xargs -r kill
    print_status "Cleanup completed"
}

# Set trap for cleanup
trap cleanup EXIT

# Check prerequisites
print_status "Checking prerequisites..."

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    print_error "Maven is not installed or not in PATH"
    exit 1
fi

# Check if Redis is available
if ! command -v redis-server &> /dev/null; then
    print_warning "Redis server not found, using embedded Redis for tests"
fi

# Check if netcat is available for port checking
if ! command -v nc &> /dev/null; then
    print_warning "netcat not found, skipping service checks"
fi

# Start Redis if available
if command -v redis-server &> /dev/null; then
    start_redis
fi

# Set Maven options
export MAVEN_OPTS="$MAVEN_OPTS"

print_status "Maven options: $MAVEN_OPTS"

# Create results directory
RESULTS_DIR="target/performance-results"
mkdir -p $RESULTS_DIR

# Run performance tests
print_status "Running performance tests..."

# Test categories to run
TESTS=(
    "com.mahjong.performance.PerformanceTestSuite#testConcurrentUserOperations"
    "com.mahjong.performance.PerformanceTestSuite#testConcurrentRoomOperations"
    "com.mahjong.performance.PerformanceTestSuite#testRedisPerformanceUnderLoad"
    "com.mahjong.performance.PerformanceTestSuite#testDatabasePerformanceUnderLoad"
    "com.mahjong.performance.PerformanceTestSuite#testSystemMetricsPerformance"
    "com.mahjong.performance.PerformanceTestSuite#testMemoryUsageUnderLoad"
)

# Run each test category
for test in "${TESTS[@]}"; do
    test_name=$(echo $test | cut -d'#' -f2)
    print_status "Running test: $test_name"
    
    start_time=$(date +%s)
    
    if mvn test -Dtest="$test" -Dspring.profiles.active=$TEST_PROFILE \
        -Dmaven.test.failure.ignore=true \
        -Dtest.output.file="$RESULTS_DIR/${test_name}.log" 2>&1 | tee "$RESULTS_DIR/${test_name}.log"; then
        
        end_time=$(date +%s)
        duration=$((end_time - start_time))
        print_status "Test $test_name completed in ${duration}s"
    else
        print_error "Test $test_name failed"
    fi
    
    # Brief pause between tests
    sleep 5
done

# Generate performance report
print_status "Generating performance report..."

REPORT_FILE="$RESULTS_DIR/performance-report.html"

cat > $REPORT_FILE << EOF
<!DOCTYPE html>
<html>
<head>
    <title>Mahjong Game Performance Test Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background-color: #f0f0f0; padding: 20px; border-radius: 5px; }
        .test-section { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
        .success { background-color: #d4edda; border-color: #c3e6cb; }
        .warning { background-color: #fff3cd; border-color: #ffeaa7; }
        .error { background-color: #f8d7da; border-color: #f5c6cb; }
        .metrics { font-family: monospace; background-color: #f8f9fa; padding: 10px; border-radius: 3px; }
        table { border-collapse: collapse; width: 100%; margin: 10px 0; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <div class="header">
        <h1>Mahjong Game Performance Test Report</h1>
        <p>Generated on: $(date)</p>
        <p>Test Profile: $TEST_PROFILE</p>
        <p>Maven Options: $MAVEN_OPTS</p>
    </div>
EOF

# Add test results to report
for test in "${TESTS[@]}"; do
    test_name=$(echo $test | cut -d'#' -f2)
    log_file="$RESULTS_DIR/${test_name}.log"
    
    if [ -f "$log_file" ]; then
        echo "<div class=\"test-section\">" >> $REPORT_FILE
        echo "<h2>$test_name</h2>" >> $REPORT_FILE
        
        # Extract performance metrics from log
        if grep -q "Performance:" "$log_file"; then
            echo "<div class=\"metrics\">" >> $REPORT_FILE
            echo "<h3>Performance Metrics:</h3>" >> $REPORT_FILE
            echo "<pre>" >> $REPORT_FILE
            grep -A 10 "Performance:" "$log_file" | head -20 >> $REPORT_FILE
            echo "</pre>" >> $REPORT_FILE
            echo "</div>" >> $REPORT_FILE
        fi
        
        # Check for test success/failure
        if grep -q "BUILD SUCCESS" "$log_file"; then
            echo "<p class=\"success\">✅ Test passed</p>" >> $REPORT_FILE
        elif grep -q "BUILD FAILURE" "$log_file"; then
            echo "<p class=\"error\">❌ Test failed</p>" >> $REPORT_FILE
        else
            echo "<p class=\"warning\">⚠️ Test status unknown</p>" >> $REPORT_FILE
        fi
        
        echo "</div>" >> $REPORT_FILE
    fi
done

# Close HTML
cat >> $REPORT_FILE << EOF
    <div class="header">
        <h2>Summary</h2>
        <p>Performance test execution completed. Check individual test sections for detailed results.</p>
        <p>For detailed logs, see files in: $RESULTS_DIR/</p>
    </div>
</body>
</html>
EOF

print_status "Performance report generated: $REPORT_FILE"

# Display summary
print_status "Performance Test Summary:"
echo "=========================="
echo "Results directory: $RESULTS_DIR"
echo "Report file: $REPORT_FILE"
echo "Test logs: $RESULTS_DIR/*.log"

# Check if any tests failed
failed_tests=0
for test in "${TESTS[@]}"; do
    test_name=$(echo $test | cut -d'#' -f2)
    log_file="$RESULTS_DIR/${test_name}.log"
    
    if [ -f "$log_file" ] && grep -q "BUILD FAILURE" "$log_file"; then
        print_error "Test failed: $test_name"
        failed_tests=$((failed_tests + 1))
    fi
done

if [ $failed_tests -eq 0 ]; then
    print_status "All performance tests completed successfully!"
    exit 0
else
    print_error "$failed_tests test(s) failed. Check the logs for details."
    exit 1
fi
EOF