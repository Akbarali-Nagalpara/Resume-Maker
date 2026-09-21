import type { ResumeData } from "../types";

interface PreviewSectionProps {
  title: string;
  children: React.ReactNode;
}

export function PreviewSection({ title, children }: PreviewSectionProps) {
  return (
    <section className="mt-6 text-[11px] leading-[1.55]">
      <h3 className="mb-2 border-b border-foreground pb-1 text-[11px] font-black uppercase tracking-widest">
        {title}
      </h3>
      {children}
    </section>
  );
}

interface ResumePreviewProps {
  resume: ResumeData;
  zoom: number;
}

export function ResumePreview({ resume, zoom }: ResumePreviewProps) {
  const scale = zoom / 100;

  return (
    <div
      className="mx-auto w-[760px] max-w-none origin-top bg-card px-16 py-14 text-foreground shadow-paper"
      style={{ transform: `scale(${scale})`, marginBottom: `${(scale - 1) * 980}px` }}
    >
      <header className="border-b-[3px] border-foreground pb-5">
        <h2 className="font-display text-4xl font-black uppercase">{resume.personal.name}</h2>
        <p className="mt-1 text-sm font-bold uppercase tracking-widest text-primary">
          {resume.personal.title}
        </p>
        <p className="mt-4 text-[11px]">
          {resume.personal.email} · {resume.personal.phone} · {resume.personal.location} ·{" "}
          {resume.personal.website}
        </p>
      </header>
      <PreviewSection title="Profile">
        <p>{resume.summary}</p>
      </PreviewSection>
      <PreviewSection title="Experience">
        {resume.experience.map((item) => (
          <div key={item.id} className="mb-6">
            <div className="flex justify-between gap-4">
              <div>
                <h4 className="font-bold">{item.role}</h4>
                <p className="text-xs font-semibold text-primary">
                  {item.company} · {item.location}
                </p>
              </div>
              <span className="text-xs font-semibold">{item.dates}</span>
            </div>
            <ul className="mt-2 list-disc space-y-1 pl-4">
              {item.bullets.map((bullet) => (
                <li key={bullet}>{bullet}</li>
              ))}
            </ul>
          </div>
        ))}
      </PreviewSection>
      <div className="grid grid-cols-2 gap-8">
        <PreviewSection title="Skills">
          <p>{resume.skills.join(" · ")}</p>
        </PreviewSection>
        <PreviewSection title="Education">
          {resume.education.map((item) => (
            <div key={item.id}>
              <h4 className="font-bold">{item.degree}</h4>
              <p>{item.school}</p>
              <p>{item.dates}</p>
            </div>
          ))}
        </PreviewSection>
      </div>
      <PreviewSection title="Projects">
        {resume.projects.map((item) => (
          <div key={item.id} className="mb-3">
            <h4 className="font-bold">{item.name}</h4>
            <p>{item.description}</p>
            <p className="font-semibold">{item.stack}</p>
          </div>
        ))}
      </PreviewSection>
    </div>
  );
}
