import type {
  EducationItem,
  ExperienceItem,
  PersonalInfo,
  ProjectItem,
  ResumeData,
} from "../types";

const API_BASE =
  (import.meta.env["VITE_API_BASE_URL"] as string | undefined)?.replace(/\/$/, "") ??
  "http://localhost:8081";

export class ResumeApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ResumeApiError";
    this.status = status;
  }

  get isConflict(): boolean {
    return this.status === 409;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }

  get isNetworkFailure(): boolean {
    return this.status === 0;
  }
}

/** A resume editing session: backend identity plus the editable model. */
export interface ResumeSession {
  resumeId: string;
  fileName: string;
  mimeType: string;
  /** Optimistic-lock version for content updates (`resume.version`). */
  version: number;
  contentVersion: number;
  content: ResumeData;
  /**
   * Raw server content payload. Spread under edited fields on save so
   * server-side sections the editor doesn't model (e.g. additionalSections)
   * round-trip untouched.
   */
  serverContent: Record<string, unknown>;
}

interface UploadResponse {
  resumeId: string;
}

interface EditorStateResponse {
  resume: { id: string; version: number; mimeType: string };
  content: Record<string, unknown>;
  latestVersion: number;
}

function problemMessage(status: number, body: string): string {
  if (!body) {
    return status === 0 ? "Network error. Is the backend running?" : `Request failed (${status}).`;
  }
  try {
    const parsed = JSON.parse(body) as { title?: string; detail?: string };
    if (parsed.detail && parsed.detail !== "An unexpected error occurred.") {
      return parsed.detail;
    }
    if (parsed.title) {
      return parsed.title;
    }
  } catch {
    // Fall through to raw body.
  }
  return body.slice(0, 300);
}

async function throwForStatus(response: Response): Promise<void> {
  if (response.ok) {
    return;
  }
  const body = await response.text().catch(() => "");
  throw new ResumeApiError(response.status, problemMessage(response.status, body));
}

function toError(status: number, body: string): ResumeApiError {
  return new ResumeApiError(status, problemMessage(status, body));
}

/**
 * Uploads the file with progress events (fetch has no upload progress, so
 * XHR is used here), then resolves with the created resume id.
 */
export function uploadResume(
  file: File,
  onProgress: (percent: number) => void,
): Promise<UploadResponse> {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("POST", `${API_BASE}/api/v1/resumes`);
    request.setRequestHeader("X-Request-Id", crypto.randomUUID());
    request.upload.addEventListener("progress", (event) => {
      if (event.lengthComputable) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    });
    request.addEventListener("load", () => {
      if (request.status >= 200 && request.status < 300) {
        try {
          resolve(JSON.parse(request.responseText) as UploadResponse);
        } catch {
          reject(new ResumeApiError(request.status, "Invalid upload response."));
        }
      } else {
        reject(toError(request.status, request.responseText));
      }
    });
    request.addEventListener("error", () => {
      reject(new ResumeApiError(0, "Network error. Is the backend running?"));
    });
    request.addEventListener("abort", () => {
      reject(new ResumeApiError(0, "Upload cancelled."));
    });
    const form = new FormData();
    form.append("file", file, file.name);
    onProgress(0);
    request.send(form);
  });
}

function normalizePersonal(raw: Record<string, unknown> | undefined): PersonalInfo {
  const text = (value: unknown): string => (typeof value === "string" ? value : "");
  return {
    name: text(raw?.["name"]),
    title: text(raw?.["title"]),
    email: text(raw?.["email"]),
    phone: text(raw?.["phone"]),
    location: text(raw?.["location"]),
    website: text(raw?.["website"]),
    github: text(raw?.["github"]),
    linkedin: text(raw?.["linkedin"]),
  };
}

function normalizeList<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

function normalizeContent(raw: Record<string, unknown>): ResumeData {
  const personal =
    raw["personal"] && typeof raw["personal"] === "object"
      ? (raw["personal"] as Record<string, unknown>)
      : {};
  const experience = normalizeList<ExperienceItem>(raw["experience"]).map((item) => ({
    ...item,
    bullets: normalizeList<string>(item.bullets),
  }));
  return {
    personal: normalizePersonal(personal),
    summary: typeof raw["summary"] === "string" ? (raw["summary"] as string) : "",
    skills: normalizeList<string>(raw["skills"]),
    experience,
    projects: normalizeList<ProjectItem>(raw["projects"]),
    education: normalizeList<EducationItem>(raw["education"]),
  };
}

export async function fetchEditorState(resumeId: string): Promise<ResumeSession> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}/api/v1/resumes/${resumeId}`);
  } catch {
    throw new ResumeApiError(0, "Network error. Is the backend running?");
  }
  await throwForStatus(response);
  const state = (await response.json()) as EditorStateResponse;
  const serverContent = (state.content ?? {}) as Record<string, unknown>;
  return {
    resumeId: state.resume.id,
    fileName: "",
    mimeType: state.resume.mimeType,
    version: state.resume.version,
    contentVersion: state.latestVersion,
    content: normalizeContent(serverContent),
    serverContent,
  };
}

export async function saveContent(
  session: ResumeSession,
  content: ResumeData,
): Promise<ResumeSession> {
  const payload = {
    expectedVersion: session.version,
    content: { ...session.serverContent, ...content },
  };
  let response: Response;
  try {
    response = await fetch(`${API_BASE}/api/v1/resumes/${session.resumeId}/content`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
  } catch {
    throw new ResumeApiError(0, "Network error. Changes were not saved.");
  }
  if (response.status === 409) {
    const body = await response.text().catch(() => "");
    throw new ResumeApiError(
      409,
      problemMessage(409, body) ||
        "This resume changed elsewhere. Reload to get the latest version.",
    );
  }
  await throwForStatus(response);
  const updated = (await response.json()) as {
    versionNumber: number;
    content: Record<string, unknown>;
  };
  const serverContent = (updated.content ?? {}) as Record<string, unknown>;
  // Refetch the resume version: content updates bump it via compare-and-touch.
  const refreshed = await fetchEditorState(session.resumeId);
  return {
    ...refreshed,
    fileName: session.fileName,
    contentVersion: updated.versionNumber,
    content: normalizeContent(serverContent),
    serverContent,
  };
}

export interface GeneratedDocument {
  blob: Blob;
  mimeType: string;
  filename: string;
  warnings: string[];
}

export function pdfPageUrl(resumeId: string, page: number): string {
  return `${API_BASE}/api/v1/resumes/${encodeURIComponent(resumeId)}/preview/pages/${page}`;
}

export async function fetchPdfPageCount(resumeId: string): Promise<number> {
  let response: Response;
  try {
    response = await fetch(
      `${API_BASE}/api/v1/resumes/${encodeURIComponent(resumeId)}/preview/page-count`,
    );
  } catch {
    throw new ResumeApiError(0, "Network error. Could not load the preview.");
  }
  await throwForStatus(response);
  const body = (await response.json()) as { pages: number };
  return body.pages;
}

function filenameFromDisposition(header: string | null, fallback: string): string {
  if (header) {
    const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/.exec(header);
    if (match?.[1]) {
      try {
        return decodeURIComponent(match[1]);
      } catch {
        return match[1];
      }
    }
  }
  return fallback;
}

/** Generates (if needed) and downloads the actual backend-produced document. */
export async function fetchGeneratedDocument(session: ResumeSession): Promise<GeneratedDocument> {
  const encodedId = encodeURIComponent(session.resumeId);
  let generateResponse: Response;
  try {
    generateResponse = await fetch(`${API_BASE}/api/v1/resumes/${encodedId}/generate`, {
      method: "POST",
    });
  } catch {
    throw new ResumeApiError(0, "Network error. Could not generate the resume.");
  }
  await throwForStatus(generateResponse);
  const generated = (await generateResponse.json()) as { warnings?: unknown };
  const warnings = Array.isArray(generated.warnings)
    ? generated.warnings.filter((w): w is string => typeof w === "string")
    : [];

  let download: Response;
  try {
    download = await fetch(`${API_BASE}/api/v1/resumes/${encodedId}/download`);
  } catch {
    throw new ResumeApiError(0, "Network error. Could not download the resume.");
  }
  await throwForStatus(download);
  const mimeType = download.headers.get("Content-Type")?.split(";")[0]?.trim() || session.mimeType;
  const extension = mimeType.includes("pdf") ? ".pdf" : ".docx";
  const base = session.fileName.replace(/\.[^.]+$/, "") || "resume";
  const filename = filenameFromDisposition(
    download.headers.get("Content-Disposition"),
    `${base}-updated${extension}`,
  );
  return { blob: await download.blob(), mimeType, filename, warnings };
}

export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 5000);
}

export function isPdf(mimeType: string): boolean {
  return mimeType.includes("pdf");
}
