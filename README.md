# Bookie

A rental income and expense tracking application for managing rental properties, tracking income and expenses, and parsing utility/vendor emails into expense records.

## Tech Stack

- **Backend:** Spring Boot 3.5, Java 21, H2 (file-based), JPA/Hibernate
- **Frontend:** React 18, React Router, Vite 6
- **AI Agent:** Anthropic API (Claude) — natural-language expense creation
- **Email Parsing:** Spring AI + Ollama (`qwen3:14b`) — structured extraction from Outlook emails. Uses `/think` in the system prompt for reliable multi-step tool calling

## Setup

### Prerequisites

- Java 21+
- Node.js 18+
- [Ollama](https://ollama.com) with the email parsing model:

```bash
ollama pull qwen3:14b
```

### Configuration

Copy `.env.example` to `.env` and fill in your values:

```bash
cp .env.example .env
```

| Variable | Description |
|---|---|
| `ANTHROPIC_API_KEY` | Required for the AI Agent feature |
| `OLLAMA_BASE_URL` | Ollama server URL (default: `http://localhost:11434`) |
| `OLLAMA_MODEL` | Ollama model for email parsing (default: `qwen3:14b`) |
| `OUTLOOK_CLIENT_ID` | Azure app client ID for Outlook integration |
| `OUTLOOK_CLIENT_SECRET` | Azure app client secret for Outlook integration |
| `OUTLOOK_TENANT_ID` | Azure tenant ID for Outlook integration |
| `OUTLOOK_REDIRECT_URI` | OAuth2 redirect URI (default: `http://localhost:8080/api/outlook/callback`) |

## Running the App

```bash
./gradlew bootRun        # builds frontend then starts Spring Boot at http://localhost:8080
cd frontend && npm run dev  # dev server at http://localhost:5173 (proxies /api to 8080)
```

## Running Tests

```bash
./gradlew test
```

## Project Structure

```
src/main/java/com/bookie/
  controller/   REST controllers
  model/        JPA entities and enums
  repository/   Spring Data JPA repositories
  service/      Business logic
frontend/
  src/          React source
diagrams/
  erd.drawio          Entity-relationship diagram
  architecture.drawio System architecture diagram
```

## Diagrams

The `diagrams/` directory contains draw.io files viewable with the diagrams.net IntelliJ plugin or by importing into Lucidchart.