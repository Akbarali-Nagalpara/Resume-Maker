import { Check, FileText } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { PARSING_STEPS } from "../constants";
import { TopBar } from "./TopBar";

interface ParsingScreenProps {
  fileName: string;
  step: number;
  uploadPercent: number | null;
  error: string | null;
  onBack: () => void;
  onRetry: () => void;
}

export function ParsingScreen({
  fileName,
  step,
  uploadPercent,
  error,
  onBack,
  onRetry,
}: ParsingScreenProps) {
  return (
    <div className="min-h-screen">
      <TopBar title="Analyzing resume">
        <Button variant="ghost" onClick={onBack}>
          Back
        </Button>
      </TopBar>
      <main className="mx-auto flex max-w-2xl flex-col items-center px-5 py-16 text-center md:py-24">
        <span className="grid size-16 place-items-center rounded-full bg-coral-soft">
          <FileText className="size-7 text-primary parsing-pulse" aria-hidden="true" />
        </span>
        <p className="mt-6 text-sm font-semibold text-muted-foreground">{fileName}</p>
        <h1 className="mt-3 font-display text-4xl font-extrabold">Preparing your resume</h1>
        <p className="mt-3 text-muted-foreground">
          Separating your content from the design—without changing either.
        </p>
        {uploadPercent !== null && !error ? (
          <p className="mt-4 text-sm font-semibold text-primary" aria-live="polite">
            Uploading… {uploadPercent}%
          </p>
        ) : null}
        {error ? (
          <div
            role="alert"
            className="mt-8 w-full border border-destructive/40 bg-card p-6 text-left"
          >
            <p className="font-bold text-destructive">Couldn’t prepare your resume</p>
            <p className="mt-2 text-sm text-muted-foreground">{error}</p>
            <div className="mt-4 flex gap-2">
              <Button onClick={onRetry}>Try again</Button>
              <Button variant="outline" onClick={onBack}>
                Choose another file
              </Button>
            </div>
          </div>
        ) : null}
        <section
          className="mt-12 w-full border border-border bg-card p-6 text-left sm:p-8"
          aria-live="polite"
        >
          <Progress
            value={((step + 1) / PARSING_STEPS.length) * 100}
            className="mb-8 h-1 bg-stone"
          />
          <div className="space-y-1">
            {PARSING_STEPS.map((label, index) => {
              const complete = index < step;
              const active = index === step;
              return (
                <div key={label} className="flex items-center gap-4 py-3">
                  <span
                    className={`grid size-7 shrink-0 place-items-center rounded-full text-xs font-bold ${
                      complete
                        ? "bg-foreground text-background"
                        : active
                          ? "bg-primary text-primary-foreground parsing-pulse"
                          : "bg-stone text-muted-foreground"
                    }`}
                  >
                    {complete ? <Check className="size-4" aria-hidden="true" /> : index + 1}
                  </span>
                  <span
                    className={
                      active || complete ? "font-semibold text-foreground" : "text-muted-foreground"
                    }
                  >
                    {label}
                  </span>
                  {active ? (
                    <span className="ml-auto text-xs font-bold uppercase tracking-widest text-primary">
                      In progress
                    </span>
                  ) : null}
                </div>
              );
            })}
          </div>
        </section>
      </main>
    </div>
  );
}
