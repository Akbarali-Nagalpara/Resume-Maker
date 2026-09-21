import { createFileRoute } from "@tanstack/react-router";
import { ResumeFlowApp } from "@/features/resume-flow/ResumeFlowApp";

// Home route inherits the base document head from __root.tsx.
export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "ResumeFlow" },
      {
        name: "description",
        content:
          "Upload your existing resume, edit its content, and preserve the original template.",
      },
      { property: "og:title", content: "ResumeFlow" },
      {
        property: "og:description",
        content:
          "Upload your existing resume, edit its content, and preserve the original template.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Index,
});

function Index() {
  return <ResumeFlowApp />;
}
