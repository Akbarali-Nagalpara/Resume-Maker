import { useEffect, useState } from "react";
import { ArrowLeft, Download, Minus, Plus, RefreshCw, ShieldCheck, Trash2, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import {
  PREVIEW_ZOOM_DEFAULT,
  PREVIEW_ZOOM_MAX,
  PREVIEW_ZOOM_MIN,
  PREVIEW_ZOOM_STEP,
} from "../constants";
import {
  downloadBlob,
  fetchEditorState,
  fetchGeneratedDocument,
  isPdf,
  ResumeApiError,
  saveContent,
  type GeneratedDocument,
  type ResumeSession,
} from "../services/resume-api";
import type {
  EducationItem,
  ExperienceItem,
  PersonalField,
  ProjectItem,
  ResumeData,
} from "../types";
import { ResumePreview } from "./ResumePreview";
import { TopBar } from "./TopBar";

function Field({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="grid gap-1.5 text-xs font-bold uppercase tracking-wide text-muted-foreground">
      {label}
      <Input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-10 bg-card text-sm font-normal normal-case text-foreground"
      />
    </label>
  );
}

function SectionError({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      className="mt-4 border-l-2 border-destructive bg-destructive/5 px-3 py-2 text-sm text-destructive"
    >
      <p>{message}</p>
      {onRetry ? (
        <Button variant="outline" size="sm" className="mt-2" onClick={onRetry}>
          <RefreshCw aria-hidden="true" /> Try again
        </Button>
      ) : null}
    </div>
  );
}

function SkillsEditor({ skills, onChange }: { skills: string[]; onChange: (v: string[]) => void }) {
  const [draft, setDraft] = useState("");
  const add = () => {
    const skill = draft.trim();
    if (!skill) {
      return;
    }
    onChange([...skills, skill]);
    setDraft("");
  };
  return (
    <div className="grid gap-3">
      {skills.length > 0 ? (
        <ul className="flex flex-wrap gap-2">
          {skills.map((skill, index) => (
            <li
              key={`${skill}-${index}`}
              className="flex items-center gap-1.5 border border-border bg-background px-2.5 py-1.5 text-sm"
            >
              {skill}
              <button
                type="button"
                aria-label={`Remove skill ${skill}`}
                className="text-muted-foreground hover:text-destructive"
                onClick={() => onChange(skills.filter((_, i) => i !== index))}
              >
                <X className="size-3.5" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted-foreground">No skills yet. Add your first one below.</p>
      )}
      <div className="flex gap-2">
        <Input
          value={draft}
          aria-label="New skill"
          placeholder="e.g. Docker"
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              add();
            }
          }}
          className="h-10 bg-card"
        />
        <Button type="button" variant="outline" onClick={add}>
          <Plus aria-hidden="true" /> Add
        </Button>
      </div>
    </div>
  );
}

function ExperienceEditor({
  items,
  onChange,
}: {
  items: ExperienceItem[];
  onChange: (v: ExperienceItem[]) => void;
}) {
  const update = (index: number, patch: Partial<ExperienceItem>) => {
    onChange(items.map((item, i) => (i === index ? { ...item, ...patch } : item)));
  };
  const remove = (index: number) => onChange(items.filter((_, i) => i !== index));
  const add = () =>
    onChange([
      ...items,
      {
        id: `exp-${Date.now()}`,
        role: "",
        company: "",
        location: "",
        dates: "",
        bullets: [],
      },
    ]);
  return (
    <div className="grid gap-4">
      {items.map((item, index) => (
        <article key={item.id} className="grid gap-3 border border-border bg-background p-4">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-bold">Role {index + 1}</h4>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="text-destructive"
              onClick={() => remove(index)}
            >
              <Trash2 aria-hidden="true" /> Remove
            </Button>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Role" value={item.role} onChange={(v) => update(index, { role: v })} />
            <Field
              label="Company"
              value={item.company ?? ""}
              onChange={(v) => update(index, { company: v })}
            />
            <Field
              label="Location"
              value={item.location ?? ""}
              onChange={(v) => update(index, { location: v })}
            />
            <Field
              label="Dates"
              value={item.dates ?? ""}
              onChange={(v) => update(index, { dates: v })}
            />
          </div>
          <label className="grid gap-1.5 text-xs font-bold uppercase tracking-wide text-muted-foreground">
            Bullets (one per line)
            <Textarea
              value={(item.bullets ?? []).join("\n")}
              onChange={(event) =>
                update(index, {
                  bullets: event.target.value
                    .split("\n")
                    .map((line) => line.trim())
                    .filter(Boolean),
                })
              }
              className="min-h-24 resize-y bg-card text-sm font-normal normal-case text-foreground"
            />
          </label>
        </article>
      ))}
      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">No experience entries yet.</p>
      ) : null}
      <div>
        <Button type="button" variant="outline" onClick={add}>
          <Plus aria-hidden="true" /> Add experience
        </Button>
      </div>
    </div>
  );
}

function ProjectsEditor({
  items,
  onChange,
}: {
  items: ProjectItem[];
  onChange: (v: ProjectItem[]) => void;
}) {
  const update = (index: number, patch: Partial<ProjectItem>) => {
    onChange(items.map((item, i) => (i === index ? { ...item, ...patch } : item)));
  };
  const remove = (index: number) => onChange(items.filter((_, i) => i !== index));
  const add = () =>
    onChange([...items, { id: `project-${Date.now()}`, name: "", description: "", stack: "" }]);
  return (
    <div className="grid gap-4">
      {items.map((item, index) => (
        <article key={item.id} className="grid gap-3 border border-border bg-background p-4">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-bold">Project {index + 1}</h4>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="text-destructive"
              onClick={() => remove(index)}
            >
              <Trash2 aria-hidden="true" /> Remove
            </Button>
          </div>
          <Field label="Name" value={item.name} onChange={(v) => update(index, { name: v })} />
          <label className="grid gap-1.5 text-xs font-bold uppercase tracking-wide text-muted-foreground">
            Description
            <Textarea
              value={item.description ?? ""}
              onChange={(event) => update(index, { description: event.target.value })}
              className="min-h-20 resize-y bg-card text-sm font-normal normal-case text-foreground"
            />
          </label>
          <Field
            label="Stack"
            value={item.stack ?? ""}
            onChange={(v) => update(index, { stack: v })}
          />
        </article>
      ))}
      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">No projects yet.</p>
      ) : null}
      <div>
        <Button type="button" variant="outline" onClick={add}>
          <Plus aria-hidden="true" /> Add project
        </Button>
      </div>
    </div>
  );
}

function EducationEditor({
  items,
  onChange,
}: {
  items: EducationItem[];
  onChange: (v: EducationItem[]) => void;
}) {
  const update = (index: number, patch: Partial<EducationItem>) => {
    onChange(items.map((item, i) => (i === index ? { ...item, ...patch } : item)));
  };
  const remove = (index: number) => onChange(items.filter((_, i) => i !== index));
  const add = () =>
    onChange([...items, { id: `education-${Date.now()}`, degree: "", school: "", dates: "" }]);
  return (
    <div className="grid gap-4">
      {items.map((item, index) => (
        <article key={item.id} className="grid gap-3 border border-border bg-background p-4">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-bold">Education {index + 1}</h4>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="text-destructive"
              onClick={() => remove(index)}
            >
              <Trash2 aria-hidden="true" /> Remove
            </Button>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field
              label="Degree"
              value={item.degree}
              onChange={(v) => update(index, { degree: v })}
            />
            <Field
              label="School"
              value={item.school ?? ""}
              onChange={(v) => update(index, { school: v })}
            />
            <Field
              label="Dates"
              value={item.dates ?? ""}
              onChange={(v) => update(index, { dates: v })}
            />
          </div>
        </article>
      ))}
      {items.length === 0 ? (
        <p className="text-sm text-muted-foreground">No education entries yet.</p>
      ) : null}
      <div>
        <Button type="button" variant="outline" onClick={add}>
          <Plus aria-hidden="true" /> Add education
        </Button>
      </div>
    </div>
  );
}

interface EditorScreenProps {
  session: ResumeSession;
  onSessionChange: (session: ResumeSession) => void;
  onBack: () => void;
}

interface DocumentState extends GeneratedDocument {
  contentVersion: number;
  objectUrl: string;
}

export function EditorScreen({ session, onSessionChange, onBack }: EditorScreenProps) {
  const [resume, setResume] = useState<ResumeData>(session.content);
  const [saved, setSaved] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [document, setDocument] = useState<DocumentState | null>(null);
  const [zoom, setZoom] = useState(PREVIEW_ZOOM_DEFAULT);

  useEffect(() => {
    return () => {
      if (document) {
        URL.revokeObjectURL(document.objectUrl);
      }
    };
  }, [document]);

  const markDirty = (next: ResumeData) => {
    setResume(next);
    setSaved(false);
    setSaveError(null);
  };

  const updatePersonal = (field: PersonalField, value: string) => {
    markDirty({ ...resume, personal: { ...resume.personal, [field]: value } });
  };

  const refreshDocument = async (current: ResumeSession): Promise<DocumentState | null> => {
    setGenerating(true);
    setGenerateError(null);
    try {
      const generated = await fetchGeneratedDocument(current);
      const next: DocumentState = {
        ...generated,
        contentVersion: current.contentVersion,
        objectUrl: URL.createObjectURL(generated.blob),
      };
      setDocument((previous) => {
        if (previous) {
          URL.revokeObjectURL(previous.objectUrl);
        }
        return next;
      });
      return next;
    } catch (error) {
      setGenerateError(error instanceof Error ? error.message : "Couldn’t generate the preview.");
      return null;
    } finally {
      setGenerating(false);
    }
  };

  const handleSave = async (): Promise<ResumeSession | null> => {
    if (saved && !saveError) {
      return session;
    }
    setSaving(true);
    setSaveError(null);
    setConflict(false);
    try {
      const updated = await saveContent(session, resume);
      onSessionChange(updated);
      setSaved(true);
      await refreshDocument(updated);
      return updated;
    } catch (error) {
      if (error instanceof ResumeApiError && error.isConflict) {
        setConflict(true);
        setSaveError("This resume changed elsewhere. Reload to get the latest version.");
      } else {
        setSaveError(error instanceof Error ? error.message : "Couldn’t save changes.");
      }
      return null;
    } finally {
      setSaving(false);
    }
  };

  const handleReload = async () => {
    setReloading(true);
    setSaveError(null);
    setConflict(false);
    try {
      const fresh = await fetchEditorState(session.resumeId);
      const next = { ...fresh, fileName: session.fileName };
      onSessionChange(next);
      setResume(next.content);
      setSaved(true);
      setDocument((previous) => {
        if (previous) {
          URL.revokeObjectURL(previous.objectUrl);
        }
        return null;
      });
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : "Couldn’t reload the resume.");
    } finally {
      setReloading(false);
    }
  };

  const handleDownload = async () => {
    setDownloading(true);
    setDownloadError(null);
    try {
      const current = saved ? session : await handleSave();
      if (!current) {
        return;
      }
      let doc = document;
      if (!doc || doc.contentVersion !== current.contentVersion) {
        doc = await refreshDocument(current);
      }
      if (!doc) {
        return;
      }
      const extension = doc.mimeType.includes("pdf") ? ".pdf" : ".docx";
      const base = session.fileName.replace(/\.[^.]+$/, "") || "resume";
      downloadBlob(doc.blob, doc.filename || `${base}-updated${extension}`);
    } catch (error) {
      setDownloadError(error instanceof Error ? error.message : "Couldn’t download the resume.");
    } finally {
      setDownloading(false);
    }
  };

  const busy = saving || generating || downloading;
  const showGeneratedPdf = document !== null && isPdf(document.mimeType);
  const docStale = document !== null && document.contentVersion !== session.contentVersion;
  const statusLabel = saving
    ? "Saving…"
    : generating
      ? "Generating…"
      : downloading
        ? "Downloading…"
        : saved
          ? "Saved"
          : "Unsaved";

  return (
    <div className="min-h-screen bg-background">
      <TopBar title={session.fileName}>
        <span className="hidden items-center gap-2 text-xs font-semibold sm:flex">
          <span
            className={`size-2 rounded-full ${saved && !busy ? "bg-foreground" : busy ? "bg-stone" : "bg-primary"}`}
          />
          {statusLabel}
        </span>
        <Button
          variant="outline"
          onClick={() => void handleSave()}
          disabled={busy || (saved && !saveError)}
          className="hidden sm:inline-flex"
        >
          {saving ? "Saving…" : "Save Changes"}
        </Button>
        <Button onClick={() => void handleDownload()} disabled={downloading}>
          <Download aria-hidden="true" />{" "}
          <span className="hidden sm:inline">
            {downloading
              ? "Downloading…"
              : session.mimeType.includes("pdf")
                ? "Download PDF"
                : "Download Resume"}
          </span>
          <span className="sm:hidden">Download</span>
        </Button>
      </TopBar>
      <div className="grid lg:h-[calc(100vh-4rem)] lg:grid-cols-[minmax(420px,0.88fr)_minmax(560px,1.12fr)]">
        <section
          className="overflow-y-auto border-r border-border bg-card"
          aria-label="Resume content editor"
        >
          <div className="border-b border-border px-5 py-5 sm:px-7">
            <Button variant="ghost" size="sm" className="-ml-3 mb-4" onClick={onBack}>
              <ArrowLeft aria-hidden="true" /> Back to upload
            </Button>
            <div className="flex items-end justify-between gap-4">
              <div>
                <p className="text-xs font-bold uppercase tracking-widest text-primary">
                  Content editor
                </p>
                <h1 className="mt-1 font-display text-2xl font-extrabold">Edit your resume</h1>
              </div>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <ShieldCheck className="size-4" aria-hidden="true" /> Layout locked
              </div>
            </div>
            {saveError ? (
              <div>
                <SectionError message={saveError} />
                {conflict ? (
                  <Button
                    variant="outline"
                    size="sm"
                    className="mt-2"
                    disabled={reloading}
                    onClick={() => void handleReload()}
                  >
                    <RefreshCw aria-hidden="true" /> {reloading ? "Reloading…" : "Reload latest"}
                  </Button>
                ) : null}
              </div>
            ) : null}
            {downloadError ? (
              <SectionError message={downloadError} onRetry={() => void handleDownload()} />
            ) : null}
          </div>
          <Accordion
            type="multiple"
            defaultValue={["personal", "summary"]}
            className="px-5 pb-12 sm:px-7"
          >
            <AccordionItem value="personal" className="border-border">
              <AccordionTrigger className="text-base font-bold hover:no-underline">
                <span>
                  <span className="mr-3 text-primary">01</span>Personal Information
                </span>
              </AccordionTrigger>
              <AccordionContent className="grid gap-4 sm:grid-cols-2">
                <Field
                  label="Full name"
                  value={resume.personal.name}
                  onChange={(v) => updatePersonal("name", v)}
                />
                <Field
                  label="Professional title"
                  value={resume.personal.title}
                  onChange={(v) => updatePersonal("title", v)}
                />
                <Field
                  label="Email"
                  value={resume.personal.email}
                  onChange={(v) => updatePersonal("email", v)}
                />
                <Field
                  label="Phone"
                  value={resume.personal.phone}
                  onChange={(v) => updatePersonal("phone", v)}
                />
                <Field
                  label="Location"
                  value={resume.personal.location}
                  onChange={(v) => updatePersonal("location", v)}
                />
                <Field
                  label="Website"
                  value={resume.personal.website}
                  onChange={(v) => updatePersonal("website", v)}
                />
              </AccordionContent>
            </AccordionItem>
            <AccordionItem value="summary">
              <AccordionTrigger className="text-base font-bold hover:no-underline">
                <span>
                  <span className="mr-3 text-primary">02</span>Professional Summary
                </span>
              </AccordionTrigger>
              <AccordionContent>
                <Textarea
                  value={resume.summary}
                  aria-label="Professional summary"
                  onChange={(event) => markDirty({ ...resume, summary: event.target.value })}
                  className="min-h-36 resize-y bg-card leading-6"
                />
              </AccordionContent>
            </AccordionItem>
            <AccordionItem value="skills">
              <AccordionTrigger className="text-base font-bold hover:no-underline">
                <span>
                  <span className="mr-3 text-primary">03</span>Skills
                </span>
              </AccordionTrigger>
              <AccordionContent>
                <SkillsEditor
                  skills={resume.skills}
                  onChange={(skills) => markDirty({ ...resume, skills })}
                />
              </AccordionContent>
            </AccordionItem>
            <AccordionItem value="experience">
              <AccordionTrigger className="text-base font-bold hover:no-underline">
                <span>
                  <span className="mr-3 text-primary">04</span>Experience
                </span>
              </AccordionTrigger>
              <AccordionContent>
                <ExperienceEditor
                  items={resume.experience}
                  onChange={(experience) => markDirty({ ...resume, experience })}
                />
              </AccordionContent>
            </AccordionItem>
            <AccordionItem value="projects">
              <AccordionTrigger className="text-base font-bold hover:no-underline">
                <span>
                  <span className="mr-3 text-primary">05</span>Projects
                </span>
              </AccordionTrigger>
              <AccordionContent>
                <ProjectsEditor
                  items={resume.projects}
                  onChange={(projects) => markDirty({ ...resume, projects })}
                />
              </AccordionContent>
            </AccordionItem>
            <AccordionItem value="education">
              <AccordionTrigger className="text-base font-bold hover:no-underline">
                <span>
                  <span className="mr-3 text-primary">06</span>Education
                </span>
              </AccordionTrigger>
              <AccordionContent>
                <EducationEditor
                  items={resume.education}
                  onChange={(education) => markDirty({ ...resume, education })}
                />
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        </section>
        <section
          className="min-h-[720px] overflow-auto bg-stone/55 p-4 sm:p-8"
          aria-label="Resume preview"
        >
          <div className="sticky top-0 z-10 mx-auto mb-5 flex w-fit items-center gap-1 border border-border bg-card p-1 shadow-sm">
            {showGeneratedPdf ? null : (
              <>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="Zoom out"
                  onClick={() =>
                    setZoom((value) => Math.max(PREVIEW_ZOOM_MIN, value - PREVIEW_ZOOM_STEP))
                  }
                >
                  <Minus aria-hidden="true" />
                </Button>
                <span className="w-14 text-center text-xs font-bold" aria-live="polite">
                  {zoom}%
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="Zoom in"
                  onClick={() =>
                    setZoom((value) => Math.min(PREVIEW_ZOOM_MAX, value + PREVIEW_ZOOM_STEP))
                  }
                >
                  <Plus aria-hidden="true" />
                </Button>
              </>
            )}
            <span className="px-2 text-xs font-bold uppercase tracking-widest text-muted-foreground">
              {generating
                ? "Generating…"
                : showGeneratedPdf
                  ? docStale
                    ? "Live preview · document outdated"
                    : "Generated PDF"
                  : document
                    ? "Live preview · DOCX ready to download"
                    : "Live preview"}
            </span>
          </div>
          {generateError ? (
            <div className="mx-auto mb-5 max-w-xl">
              <SectionError message={generateError} onRetry={() => void handleSave()} />
            </div>
          ) : null}
          <div className="overflow-x-auto">
            {showGeneratedPdf && document ? (
              <iframe
                title="Generated resume PDF"
                src={document.objectUrl}
                className="mx-auto min-h-[900px] w-full max-w-3xl border border-border bg-card shadow-paper"
              />
            ) : (
              <ResumePreview resume={resume} zoom={zoom} />
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
