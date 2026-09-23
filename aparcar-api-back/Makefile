.PHONY: setup-brew setup-pip tools fmt check test build migrate

# --- COMMON TOOLS (Internal) ---
tools:
	@echo "🛠️  Installing development tools..."
	@echo "Note: pre-commit will be installed via setup-brew or setup-pip"

# --- SETUP OPTION A: HOMEBREW (Mac / LinuxBrew) ---
setup-brew:
	@echo "🍺 Installing pre-commit and graphviz via Homebrew..."
	brew install pre-commit graphviz
	pre-commit install
	@echo "✅ Setup (Brew) complete."

# --- SETUP OPTION B: PIP (Windows / Linux / Any) ---
setup-pip:
	@echo "🐍 Installing pre-commit via Pip..."
	pip install pre-commit
	pre-commit install
	@echo "⚠️  Note: Windows users need to install Graphviz manually (winget install Graphviz)"
	@echo "✅ Setup (Pip) complete."

# --- SHORTCUT (Defaults to brew if running just 'make setup') ---
setup: setup-brew

# --- DAILY WORKFLOW ---
# Run 'make fmt' to format and check code
fmt:
	@echo "🧹 Running manual pre-commit..."
	pre-commit run --all-files

# Create a new migration based on entity changes
# Usage: make migrate name=add-user-table
migrate:
	@if [ -z "$(name)" ]; then echo "Usage: make migrate name=migration-name"; exit 1; fi
	go run scripts/create-migration.go -name=$(name)

# Run the app locally
run:
	mvn spring-boot:run

# Run tests
test:
	mvn test

cover:
	@echo "🛡️  Running tests and checking coverage..."
	mvn verify
	@echo "To view coverage report: open target/site/jacoco/index.html"

# Build the project
build:
	mvn clean package -DskipTests

# Run with profiling (JFR)
profile:
	@echo "🔬 Running with JFR profiling (60 seconds)..."
	@echo "The application will exit after 60s and save 'recording.jfr'"
	java -XX:StartFlightRecording=duration=60s,filename=recording.jfr -jar target/*.jar
	@echo "To view: open recording.jfr with JDK Mission Control or IntelliJ"
