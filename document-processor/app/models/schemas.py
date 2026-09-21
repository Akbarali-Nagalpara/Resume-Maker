"""Shared types for the document processor.

The JSON shapes intentionally mirror the Spring Boot records
(ResumeContentModel / TemplateMetadata / ParsedResume) so the backend can
consume processor output without field remapping.
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class PersonalInfo:
    name: str = "Unnamed"
    title: str | None = None
    email: str | None = None
    phone: str | None = None
    location: str | None = None
    website: str | None = None
    github: str | None = None
    linkedin: str | None = None


@dataclass
class ExperienceItem:
    id: str = ""
    role: str = ""
    company: str | None = None
    location: str | None = None
    dates: str | None = None
    bullets: list[str] = field(default_factory=list)


@dataclass
class ProjectItem:
    id: str = ""
    name: str = ""
    description: str | None = None
    stack: str | None = None


@dataclass
class EducationItem:
    id: str = ""
    degree: str = ""
    school: str | None = None
    dates: str | None = None


@dataclass
class AdditionalSection:
    key: str = ""
    title: str = ""
    paragraphs: list[str] = field(default_factory=list)


@dataclass
class ResumeContent:
    personal: PersonalInfo = field(default_factory=PersonalInfo)
    summary: str | None = None
    skills: list[str] = field(default_factory=list)
    experience: list[ExperienceItem] = field(default_factory=list)
    projects: list[ProjectItem] = field(default_factory=list)
    education: list[EducationItem] = field(default_factory=list)
    additionalSections: list[AdditionalSection] = field(default_factory=list)


@dataclass
class PageGeometry:
    widthPoints: float = 595.0
    heightPoints: float = 842.0
    marginTopPoints: float = 72.0
    marginBottomPoints: float = 72.0
    marginLeftPoints: float = 72.0
    marginRightPoints: float = 72.0


@dataclass
class SectionAnchor:
    key: str = ""
    title: str = ""
    position: int = 0
    sourceNodeId: str = ""


@dataclass
class TemplateModel:
    documentType: str = "PDF"
    sourceChecksum: str = ""
    page: PageGeometry = field(default_factory=PageGeometry)
    sections: list[SectionAnchor] = field(default_factory=list)
    mappings: dict = field(default_factory=dict)
    properties: dict = field(default_factory=dict)


@dataclass
class SectionIndex:
    key: str = ""
    title: str = ""
    position: int = 0


@dataclass
class ProcessedDocument:
    documentType: str = "PDF"
    content: ResumeContent = field(default_factory=ResumeContent)
    template: TemplateModel = field(default_factory=TemplateModel)
    sections: list[SectionIndex] = field(default_factory=list)
