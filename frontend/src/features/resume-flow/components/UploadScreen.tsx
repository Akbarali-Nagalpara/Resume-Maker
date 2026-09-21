import { useRef, useState } from "react";
import { ArrowLeft, ArrowRight, FileText, Upload, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ACCEPTED_EXTENSIONS, MAX_FILE_SIZE_MB, type AcceptedExtension } from "../constants";
import { TopBar } from "./TopBar";

function getExtension(fileName: string): string {
  return fileName.split(".").pop()?.toLowerCase() ?? "";
}

function isAcceptedExtension(extension: string): extension is AcceptedExtension {
  return (ACCEPTED_EXTENSIONS as readonly string[]).includes(extension);
}

interface UploadScreenProps {
  onBack: () => void;
  onAnalyze: (file: File) => void;
}

export function UploadScreen({ onBack, onAnalyze }: UploadScreenProps) {
  const pickerRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");

  const selectFile = (selected?: File) => {
    if (!selected) return;
    const extension = getExtension(selected.name);
    if (!isAcceptedExtension(extension)) {
      setError("This file type is not supported. Choose a PDF, DOC, or DOCX file.");
      setFile(null);
      return;
    }
    if (selected.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
      setError(`Files larger than ${MAX_FILE_SIZE_MB} MB are not supported.`);
      setFile(null);
      return;
    }
    setError("");
    setFile(selected);
  };

  return (
    <div className="min-h-screen">
      <TopBar title="Upload resume">
        <Button variant="ghost" onClick={onBack}>
          <ArrowLeft aria-hidden="true" /> Back
        </Button>
      </TopBar>
      <main className="mx-auto flex max-w-3xl flex-col items-center px-5 py-14 md:py-20">
        <div className="mb-8 text-center">
          <p className="mb-3 text-xs font-bold uppercase tracking-widest text-primary">
            Start with your original
          </p>
          <h1 className="font-display text-4xl font-extrabold">Upload your resume</h1>
          <p className="mt-3 text-muted-foreground">
            We’ll extract the content while preserving its existing layout and design.
          </p>
        </div>
        <section className="w-full border border-border bg-card p-5 sm:p-8">
          <input
            ref={pickerRef}
            type="file"
            accept=".pdf,.doc,.docx"
            className="sr-only"
            aria-label="Choose a resume file"
            onChange={(event) => selectFile(event.target.files?.[0])}
          />
          {!file ? (
            <button
              type="button"
              onClick={() => pickerRef.current?.click()}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => {
                event.preventDefault();
                selectFile(event.dataTransfer.files[0]);
              }}
              className="flex min-h-72 w-full cursor-pointer flex-col items-center justify-center border-2 border-dashed border-input bg-background px-6 text-center transition-colors hover:border-primary hover:bg-accent/40"
            >
              <span className="mb-5 grid size-14 place-items-center rounded-full bg-stone">
                <Upload className="size-6" aria-hidden="true" />
              </span>
              <strong className="font-display text-xl">Drop your resume here</strong>
              <span className="mt-2 text-sm text-muted-foreground">
                or <span className="font-bold text-primary underline">browse files</span> from your
                device
              </span>
              <span className="mt-7 text-xs font-medium text-muted-foreground">
                PDF, DOC, or DOCX · Up to {MAX_FILE_SIZE_MB} MB
              </span>
            </button>
          ) : (
            <div className="flex min-h-72 flex-col items-center justify-center border border-border bg-background px-6 text-center">
              <span className="mb-5 grid size-14 place-items-center rounded-full bg-coral-soft">
                <FileText className="size-6 text-primary" aria-hidden="true" />
              </span>
              <strong className="max-w-full truncate font-display text-xl">{file.name}</strong>
              <span className="mt-2 text-sm text-muted-foreground">
                {file.name.split(".").pop()?.toUpperCase()} · {(file.size / 1024 / 1024).toFixed(2)}{" "}
                MB
              </span>
              <Button
                variant="ghost"
                className="mt-4 text-destructive"
                onClick={() => setFile(null)}
              >
                <X aria-hidden="true" /> Remove file
              </Button>
            </div>
          )}
          {error ? (
            <p
              role="alert"
              className="mt-4 border-l-2 border-destructive bg-destructive/5 px-3 py-2 text-sm text-destructive"
            >
              {error}
            </p>
          ) : null}
          <div className="mt-6 flex items-center justify-between gap-4">
            <p className="hidden max-w-sm text-xs leading-5 text-muted-foreground sm:block">
              Your file is used only to create this editing session. No template is applied.
            </p>
            <Button
              size="lg"
              disabled={!file}
              onClick={() => file && onAnalyze(file)}
              className="ml-auto h-11 px-6"
            >
              Analyze Resume <ArrowRight aria-hidden="true" />
            </Button>
          </div>
        </section>
      </main>
    </div>
  );
}
