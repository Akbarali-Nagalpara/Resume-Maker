import type { ReactNode } from "react";
import { FileText } from "lucide-react";

export function Brand() {
  return (
    <div className="flex items-center gap-2.5 font-display text-lg font-extrabold">
      <span className="grid size-8 place-items-center rounded-md bg-foreground text-background">
        <FileText className="size-4" aria-hidden="true" />
      </span>
      ResumeFlow
    </div>
  );
}

interface TopBarProps {
  title?: string;
  children?: ReactNode;
}

export function TopBar({ title, children }: TopBarProps) {
  return (
    <header className="sticky top-0 z-20 flex min-h-16 items-center justify-between border-b border-border bg-background/95 px-5 backdrop-blur md:px-8">
      <div className="flex min-w-0 items-center gap-8">
        <Brand />
        {title ? (
          <span className="hidden truncate border-l border-border pl-8 text-sm font-medium md:block">
            {title}
          </span>
        ) : null}
      </div>
      <div className="flex items-center gap-2">{children}</div>
    </header>
  );
}
