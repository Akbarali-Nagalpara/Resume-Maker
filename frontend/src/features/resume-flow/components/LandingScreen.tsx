import { ArrowRight, CheckCircle2, ChevronDown, LayoutTemplate, ShieldCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { TopBar } from "./TopBar";

const EDITOR_SECTIONS = ["Personal information", "Summary", "Experience", "Skills"];
const PREVIEW_HEADINGS = ["PROFILE", "EXPERIENCE", "EDUCATION"] as const;

function ResumeVisual() {
  return (
    <section className="relative min-h-[560px]" aria-label="Resume editor illustration">
      <div className="absolute left-0 top-20 z-10 w-52 border border-foreground bg-stone p-4 shadow-[8px_8px_0_var(--ink)] sm:w-64">
        <div className="mb-4 flex items-center justify-between text-xs font-bold uppercase">
          <span>Content editor</span>
          <ChevronDown className="size-4" aria-hidden="true" />
        </div>
        {EDITOR_SECTIONS.map((label, index) => (
          <div
            key={label}
            className="mb-2 flex items-center gap-3 border border-border bg-card p-3 text-xs font-semibold"
          >
            <span className={index === 0 ? "size-2 bg-primary" : "size-2 bg-stone"} />
            {label}
          </div>
        ))}
      </div>
      <div className="absolute right-0 top-0 h-[540px] w-[78%] max-w-md border border-border bg-card p-9 shadow-paper sm:p-12">
        <div className="border-b-2 border-foreground pb-5">
          <div className="h-5 w-2/3 bg-foreground" />
          <div className="mt-3 h-2 w-1/2 bg-primary" />
        </div>
        {PREVIEW_HEADINGS.map((heading, index) => (
          <div key={heading} className="mt-7">
            <p className="mb-3 text-[9px] font-black tracking-widest">{heading}</p>
            <div className="space-y-2">
              <div className="h-2 w-full bg-stone" />
              <div className="h-2 w-11/12 bg-stone" />
              {index === 1 ? (
                <>
                  <div className="mt-4 h-2 w-1/3 bg-foreground" />
                  <div className="h-2 w-4/5 bg-stone" />
                </>
              ) : null}
            </div>
          </div>
        ))}
      </div>
      <div className="absolute bottom-7 right-4 z-10 flex items-center gap-2 border border-border bg-card px-4 py-3 text-xs font-bold shadow-lg">
        <CheckCircle2 className="size-4 text-primary" aria-hidden="true" /> Template locked
      </div>
    </section>
  );
}

interface LandingScreenProps {
  onUpload: () => void;
}

export function LandingScreen({ onUpload }: LandingScreenProps) {
  return (
    <div className="min-h-screen bg-background">
      <TopBar />
      <main className="mx-auto grid min-h-[calc(100vh-4rem)] max-w-7xl items-center gap-12 overflow-hidden px-5 py-12 lg:grid-cols-[0.85fr_1.15fr] lg:px-8 lg:py-16">
        <section className="relative z-10 max-w-2xl">
          <div className="mb-8 inline-flex items-center gap-2 border-b border-foreground pb-2 text-xs font-bold uppercase tracking-widest">
            <LayoutTemplate className="size-4" aria-hidden="true" /> Your resume, still yours
          </div>
          <h1 className="font-display text-5xl font-extrabold leading-[0.98] sm:text-6xl lg:text-7xl">
            Update your resume without rebuilding it.
          </h1>
          <p className="mt-7 max-w-xl text-lg leading-8 text-muted-foreground">
            Upload the resume you already have, edit the content that matters, and keep the original
            design exactly as it is.
          </p>
          <div className="mt-9 flex flex-wrap items-center gap-5">
            <Button size="lg" onClick={onUpload} className="h-12 px-6 text-base shadow-none">
              Upload Resume <ArrowRight aria-hidden="true" />
            </Button>
            <span className="flex items-center gap-2 text-sm font-medium">
              <ShieldCheck className="size-4 text-primary" aria-hidden="true" /> Original template
              preserved
            </span>
          </div>
          <div className="mt-14 flex gap-8 border-t border-border pt-6 text-sm">
            <div>
              <strong className="block font-display text-xl">01</strong>
              <span className="text-muted-foreground">Upload</span>
            </div>
            <div>
              <strong className="block font-display text-xl">02</strong>
              <span className="text-muted-foreground">Edit content</span>
            </div>
            <div>
              <strong className="block font-display text-xl">03</strong>
              <span className="text-muted-foreground">Download</span>
            </div>
          </div>
        </section>
        <ResumeVisual />
      </main>
    </div>
  );
}
