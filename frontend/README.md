# ResumeFlow Frontend

Upload an existing resume, edit its content, and keep the original design intact.

Core flow: Upload → Parse → Edit → Preview → Save → Download.

## Stack

- React + TypeScript
- TanStack Start (file-based routing in `src/routes`, Vite build)
- Tailwind CSS v4 (`src/styles.css`)

## Development

```sh
npm i
npm run dev
```

## Checks

```sh
npm run typecheck
npm run lint
npm run build
```

## Backend integration

The UI depends on the `ResumeService` interface
(`src/features/resume-flow/services/resume-service.ts`).
`mockResumeService` is the current local implementation.

To connect the Java Spring Boot REST API later:

1. Copy `.env.example` to `.env` and set `VITE_API_BASE_URL`.
2. Add an HTTP `ResumeService` implementation using that base URL.
3. Swap it in where `mockResumeService` is used. No screen changes required.

## Theme

- Paper `#F2F1ED`
- Stone `#DBD5CA`
- Coral `#EF5848`
- Black `#000000`
- White `#FFFFFF`
