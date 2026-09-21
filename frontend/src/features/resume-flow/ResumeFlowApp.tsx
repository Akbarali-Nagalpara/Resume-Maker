import { useCallback, useRef, useState } from "react";
import type { AppScreen } from "./types";
import { fetchEditorState, uploadResume, type ResumeSession } from "./services/resume-api";
import { EditorScreen } from "./components/EditorScreen";
import { LandingScreen } from "./components/LandingScreen";
import { ParsingScreen } from "./components/ParsingScreen";
import { UploadScreen } from "./components/UploadScreen";

export function ResumeFlowApp() {
  const [screen, setScreen] = useState<AppScreen>("landing");
  const [file, setFile] = useState<File | null>(null);
  const [parseStep, setParseStep] = useState(0);
  const [uploadPercent, setUploadPercent] = useState<number | null>(null);
  const [parseError, setParseError] = useState<string | null>(null);
  const [session, setSession] = useState<ResumeSession | null>(null);
  const runId = useRef(0);

  const analyze = useCallback(async (selectedFile: File) => {
    const run = ++runId.current;
    setFile(selectedFile);
    setParseError(null);
    setUploadPercent(0);
    setParseStep(0);
    setScreen("parsing");
    try {
      const { resumeId } = await uploadResume(selectedFile, (percent) => {
        if (runId.current === run) {
          setUploadPercent(percent);
          setParseStep(percent < 100 ? 0 : 1);
        }
      });
      if (runId.current !== run) {
        return;
      }
      setUploadPercent(null);
      setParseStep(2);
      const editorState = await fetchEditorState(resumeId);
      if (runId.current !== run) {
        return;
      }
      setParseStep(3);
      setSession({ ...editorState, fileName: selectedFile.name });
      setScreen("editor");
    } catch (error) {
      if (runId.current !== run) {
        return;
      }
      setUploadPercent(null);
      setParseError(error instanceof Error ? error.message : "Couldn’t prepare your resume.");
    }
  }, []);

  const retry = useCallback(() => {
    if (file) {
      void analyze(file);
    } else {
      setScreen("upload");
    }
  }, [analyze, file]);

  if (screen === "landing") return <LandingScreen onUpload={() => setScreen("upload")} />;
  if (screen === "upload")
    return <UploadScreen onBack={() => setScreen("landing")} onAnalyze={analyze} />;
  if (screen === "parsing" && file) {
    return (
      <ParsingScreen
        fileName={file.name}
        step={parseStep}
        uploadPercent={uploadPercent}
        error={parseError}
        onBack={() => setScreen("upload")}
        onRetry={retry}
      />
    );
  }
  if (screen === "editor" && session) {
    return (
      <EditorScreen
        key={session.resumeId}
        session={session}
        onSessionChange={setSession}
        onBack={() => setScreen("upload")}
      />
    );
  }
  return <LandingScreen onUpload={() => setScreen("upload")} />;
}
