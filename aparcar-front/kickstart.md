# Kickstart Guide

Welcome to the **next-app-template** project! This document will help you set up your local development environment quickly.

## Prerequisites

Make sure you have the following installed on your machine:
- [Node.js](https://nodejs.org/) (Version 18 or higher)
- [Task](https://taskfile.dev/) (Optional, but highly recommended as a task runner)
- [pre-commit](https://pre-commit.com/) (For Git hooks)
  - Usually installed via `pip install pre-commit` or `brew install pre-commit`.

## Getting Started

### 1. Initial Setup

If you have Task installed, you can set up everything in one step:
```bash
task setup
```

**What this does:**
- Runs `npm install` to download dependencies.
- Runs `pre-commit install` to set up the Git hooks.

If you are not using Task, run these manually:
```bash
npm install
pre-commit install
```

### 2. Run the Development Server

Start the local Next.js development server:
```bash
task dev
# or naturally: npm run dev
```

The application will be running at [http://localhost:3000](http://localhost:3000).

---

## Available Commands

We use a `Taskfile.yml` to simplify running scripts. You can run `task --list` to see available commands or refer to the list below:

| Command | Description |
|---|---|
| `task setup` | Installs dependencies and configures `pre-commit` hooks. |
| `task dev` | Starts the Next.js development server. |
| `task build` | Builds the Next.js application for production. |
| `task start` | Starts the built production server. |
| `task lint` | Evaluates the codebase using ESLint. |
| `task install` | Only runs npm install. |

## Pre-commit Hooks

This project is configured with `pre-commit` to maintain code standards. Every time you make a commit, the following checks will automatically run:
- Fixes trailing whitespace.
- Ensures all files end with a newline.
- Validates YAML syntax.
- Runs the Next.js linter (`npm run lint`).

If a hook fails, the commit will be blocked. Don't worry! Simply fix the reported issues (or stage the changes if `pre-commit` auto-fixed them for you) and commit again.
