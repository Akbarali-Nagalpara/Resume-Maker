export const PARSING_STEPS = [
  "Reading resume",
  "Detecting sections and content",
  "Capturing template and layout",
  "Preparing editable resume",
] as const;

export const ACCEPTED_EXTENSIONS = ["pdf", "doc", "docx"] as const;

export type AcceptedExtension = (typeof ACCEPTED_EXTENSIONS)[number];

export const MAX_FILE_SIZE_MB = 10;

export const PREVIEW_ZOOM_MIN = 60;
export const PREVIEW_ZOOM_MAX = 110;
export const PREVIEW_ZOOM_STEP = 10;
export const PREVIEW_ZOOM_DEFAULT = 80;
