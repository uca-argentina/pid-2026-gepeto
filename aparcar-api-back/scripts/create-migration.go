package main

import (
	"bufio"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
)

const (
	masterChangelogPath   = "src/main/resources/db/changelog/db.changelog-master.yaml"
	changelogDir          = "src/main/resources/db/changelog/"
	yamlIncludeDir        = "db/changelog/"
	nameRegex             = `^[a-z0-9-]+$`
	changelogIncludeRegex = `file:\s*db/changelog/(\d{3})-[a-z0-9-]+\.yaml`
	maxNameLength         = 50
)

// loadEnv carga las variables de un archivo .env y valida las requeridas
func loadEnv(envPath string) map[string]string {
	envMap := make(map[string]string)
	file, err := os.Open(envPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error opening .env file at %s: %v\n", envPath, err)
		os.Exit(1)
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}

		parts := strings.SplitN(line, "=", 2)
		if len(parts) != 2 {
			continue
		}

		key := strings.TrimSpace(parts[0])
		value := strings.TrimSpace(parts[1])

		if commentIndex := strings.Index(value, " #"); commentIndex != -1 {
			value = strings.TrimSpace(value[:commentIndex])
		}

		value = strings.Trim(value, " \"'")
		envMap[key] = value
	}
	if err := scanner.Err(); err != nil {
		fmt.Fprintf(os.Stderr, "Error reading .env file: %v\n", err)
		os.Exit(1)
	}

	requiredKeys := []string{"DB_URL", "DB_USERNAME", "DB_PASSWORD"}
	for _, key := range requiredKeys {
		if val, ok := envMap[key]; !ok || val == "" {
			fmt.Fprintf(os.Stderr, "Error: Missing or empty required variable in %s: %s\n", envPath, key)
			os.Exit(1)
		}
	}
	return envMap
}

// getNextMigrationNumber lee el master changelog y devuelve el siguiente número (ej: "002")
func getNextMigrationNumber(masterFile string) string {
	file, err := os.Open(masterFile)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error opening master changelog %s: %v\n", masterFile, err)
		os.Exit(1)
	}
	defer file.Close()

	re := regexp.MustCompile(changelogIncludeRegex)
	lastNumber := 0
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := scanner.Text()
		matches := re.FindStringSubmatch(line)
		if len(matches) == 2 {
			num, err := strconv.Atoi(matches[1])
			if err == nil && num > lastNumber {
				lastNumber = num
			}
		}
	}
	if err := scanner.Err(); err != nil {
		fmt.Fprintf(os.Stderr, "Error reading master changelog: %v\n", err)
		os.Exit(1)
	}

	nextNumber := lastNumber + 1
	return fmt.Sprintf("%03d", nextNumber)
}

// appendToMasterChangelog agrega la nueva línea de include al archivo master
func appendToMasterChangelog(masterFile, includePath string) error {
	f, err := os.OpenFile(masterFile, os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		return fmt.Errorf("could not open master changelog for appending: %w", err)
	}
	defer f.Close()

	includeEntry := fmt.Sprintf("\n  - include:\n      file: %s\n", includePath)

	if _, err := f.WriteString(includeEntry); err != nil {
		return fmt.Errorf("could not write to master changelog: %w", err)
	}
	return nil
}

func main() {
	var migrationName, envFilePath string

	// 1. Definir y parsear flags
	flag.StringVar(&migrationName, "name", "", "Migration name (e.g., add-notes-to-transaction)")
	flag.StringVar(&envFilePath, "env-file", ".env", "Path to the .env file")
	flag.Parse()

	// 2. Validar el nombre de la migración
	if migrationName == "" {
		fmt.Fprintln(os.Stderr, "Error: -name flag is required.\nUsage: go run create-migration.go -name=<migration-name>")
		os.Exit(1)
	}
	if !regexp.MustCompile(nameRegex).MatchString(migrationName) {
		fmt.Fprintf(os.Stderr, "Error: Invalid migration name '%s'. Must match regex: %s\n", migrationName, nameRegex)
		os.Exit(1)
	}
	if len(migrationName) > maxNameLength {
		fmt.Fprintf(os.Stderr, "Error: Migration name exceeds max length of %d chars.\n", maxNameLength)
		os.Exit(1)
	}

	// 3. Cargar y validar .env
	envVars := loadEnv(envFilePath)

	// 4. Calcular el siguiente número de versión
	nextNumberStr := getNextMigrationNumber(masterChangelogPath)
	newFileName := fmt.Sprintf("%s-%s.yaml", nextNumberStr, migrationName)

	// 5. Preparar rutas
	fullDiffFilePath := filepath.Join(changelogDir, newFileName)
	diffFileArg := strings.ReplaceAll(fullDiffFilePath, "\\", "/")

	yamlIncludePath := fmt.Sprintf("%s%s", yamlIncludeDir, newFileName)
	yamlIncludePath = strings.ReplaceAll(yamlIncludePath, "\\", "/")

	// 6. Preparar los argumentos del comando Maven
	url := fmt.Sprintf("-Dliquibase.url=%s", envVars["DB_URL"])
	user := fmt.Sprintf("-Dliquibase.username=%s", envVars["DB_USERNAME"])
	pass := fmt.Sprintf("-Dliquibase.password=%s", envVars["DB_PASSWORD"])
	diffFile := fmt.Sprintf("-Dliquibase.diffChangeLogFile=%s", diffFileArg)

	// 7. Compilar primero para asegurar que las entidades estén actualizadas
	fmt.Println("Compiling entities...")
	compileCmd := exec.Command("mvn", "clean", "compile", "-DskipTests")
	compileCmd.Stdout = os.Stdout
	compileCmd.Stderr = os.Stderr
	if err := compileCmd.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "\nError compiling: %v\n", err)
		os.Exit(1)
	}

	fmt.Printf("\nRunning Liquibase diff...\n")
	fmt.Printf("New migration file will be: %s\n\n", diffFileArg)

	// 8. Construir y ejecutar el comando
	cmd := exec.Command(
		"mvn", "liquibase:diff",
		url,
		user,
		pass,
		diffFile,
	)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	if err := cmd.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "\nError running mvn liquibase:diff: %v\n", err)
		os.Exit(1)
	}

	fmt.Printf("\nSuccessfully generated migration: %s\n", diffFileArg)

	// 9. Actualizar el master changelog
	fmt.Printf("Appending to %s...\n", masterChangelogPath)
	if err := appendToMasterChangelog(masterChangelogPath, yamlIncludePath); err != nil {
		fmt.Fprintf(os.Stderr, "\nError updating master changelog: %v\n", err)
		os.Exit(1)
	}

	fmt.Println("¡Proceso completado! Migración generada y registrada.")
}
