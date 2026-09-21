export type AppScreen = "landing" | "upload" | "parsing" | "editor";

export type ResumeFileType = "PDF" | "DOC" | "DOCX";

export interface ResumeFile {
  name: string;
  size: number;
  type: ResumeFileType;
}

export interface PersonalInfo {
  name: string;
  title: string;
  email: string;
  phone: string;
  location: string;
  website: string;
  github: string;
  linkedin: string;
}

export type PersonalField = keyof PersonalInfo;

export interface ExperienceItem {
  id: string;
  role: string;
  company: string;
  location: string;
  dates: string;
  bullets: string[];
}

export interface ProjectItem {
  id: string;
  name: string;
  description: string;
  stack: string;
}

export interface EducationItem {
  id: string;
  degree: string;
  school: string;
  dates: string;
}

export interface ResumeData {
  personal: PersonalInfo;
  summary: string;
  skills: string[];
  experience: ExperienceItem[];
  projects: ProjectItem[];
  education: EducationItem[];
}
