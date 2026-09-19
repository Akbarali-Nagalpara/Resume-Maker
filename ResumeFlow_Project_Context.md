# ResumeFlow — Project Context

## 1. Project Overview

ResumeFlow is a web-based resume management and generation platform designed to make updating resumes fast and easy.

The main problem it solves is the repeated manual effort required to update a resume, especially resumes created using LaTeX.

A developer may frequently:

- Learn a new technology
- Build a new project
- Gain a certification
- Add internship/work experience
- Add new skills
- Improve project descriptions
- Modify achievements

With a traditional LaTeX resume, every update requires manually editing `.tex` files, maintaining formatting, compiling the document, checking the generated PDF, and downloading the updated resume.

ResumeFlow separates **resume content from resume presentation**.

The user maintains structured resume information through a web interface while ResumeFlow preserves the selected resume template and generates an updated PDF automatically.

### Core Value Proposition

> Update your resume in seconds without manually editing LaTeX.

---

# 2. Product Direction

ResumeFlow will be a **web-first application**.

The initial product should focus on desktop/laptop usage because resume editing, previewing, formatting, and PDF generation are more convenient on larger screens.

The UI should still be responsive and usable on tablets and mobile browsers.

A native mobile application is not part of the initial MVP.

---

# 3. Target Users

Primary users:

- Software developers
- Students
- Fresh graduates
- Interns
- Job seekers
- Engineers who frequently update their resumes

The initial target audience is technical users who maintain resumes in LaTeX or structured templates.

---

# 4. Core Problem

Current workflow:

```text
Learn new technology
        ↓
Open LaTeX source
        ↓
Find correct section
        ↓
Modify content
        ↓
Maintain formatting
        ↓
Compile LaTeX
        ↓
Check PDF
        ↓
Fix formatting issues
        ↓
Download PDF
```

ResumeFlow should reduce this to:

```text
Open Resume
      ↓
Edit Resume Information
      ↓
Save
      ↓
Generate PDF
      ↓
Download PDF
```

---

# 5. Core Product Concept

ResumeFlow stores two major things separately:

```text
Resume Data
     +
Resume Template
     ↓
Resume Generator
     ↓
PDF
```

Resume data contains the actual information.

Example:

```json
{
  "personal": {
    "name": "",
    "email": "",
    "phone": "",
    "linkedin": "",
    "github": ""
  },
  "summary": "",
  "skills": {
    "languages": [],
    "frameworks": [],
    "databases": [],
    "tools": []
  },
  "experience": [],
  "projects": [],
  "education": [],
  "certifications": [],
  "achievements": []
}
```

The template controls how this information is presented.

This separation allows users to update content without manually editing the underlying formatting.

---

# 6. Initial MVP

The MVP should include:

## Authentication

- User registration
- Login
- Logout
- Password security
- JWT-based authentication

## Resume Management

Users should be able to:

- Create a resume
- Upload an existing resume
- View resumes
- Edit resumes
- Duplicate resumes
- Delete resumes
- Rename resumes

## Resume Sections

The initial supported sections should include:

1. Personal Information
2. Professional Summary
3. Skills
4. Experience
5. Projects
6. Education
7. Certifications
8. Achievements
9. Custom Sections

## Resume Editor

The editor should allow users to:

- Add information
- Edit information
- Delete information
- Reorder relevant sections
- Add/remove skills
- Add/remove projects
- Add/remove experience
- Save changes

## Live Preview

The user should be able to see the resume preview while editing.

The editor should not feel like a generic document editor.

It should be a structured resume editor.

## PDF Generation

After saving:

```text
Resume Data
    ↓
Template
    ↓
Generated Document
    ↓
PDF
```

The user should be able to download the generated PDF.

---

# 7. LaTeX Support

LaTeX is an important part of the product.

The initial system should support LaTeX-based resume templates.

If a user uploads a `.tex` resume, the system should attempt to separate:

```text
Resume Content
```

from:

```text
LaTeX Formatting / Template
```

The long-term goal is:

```text
Existing LaTeX Resume
        ↓
Parse
        ↓
Extract Resume Data
        +
Preserve Template
        ↓
Structured Resume
```

However, automatic parsing of arbitrary LaTeX documents should not block the MVP.

The first implementation may support a controlled LaTeX template format.

---

# 8. Resume Templates

Resume templates should be treated as independent presentation layers.

Example:

```text
Template A
Template B
Template C
```

A user's resume data should be reusable across templates.

Example:

```text
Resume Data
     │
     ├── Template A → PDF
     ├── Template B → PDF
     └── Template C → PDF
```

---

# 9. Resume Version History

Every meaningful resume update should be versioned.

Example:

```text
v1.0 — Initial Resume
v1.1 — Added Docker
v1.2 — Added Kafka
v1.3 — Added Kubernetes
v1.4 — Added New Project
```

Users should eventually be able to:

- View versions
- Compare versions
- Restore previous versions

Version history may be implemented after the basic MVP editor and PDF generation are working.

---

# 10. Multiple Resume Variants

The platform should support multiple resumes for the same user.

Example:

```text
Backend Developer Resume
Full Stack Developer Resume
AI/ML Resume
Software Engineer Resume
```

Each resume may share some information but can have different content and templates.

This is important because users may maintain different resumes for different job types.

---

# 11. Future AI Features

AI should not be required for the first MVP.

Future AI capabilities may include:

### AI Skill Update

User:

> I learned Kafka and Kubernetes.

System:

- Detect relevant resume section
- Suggest where the technologies belong
- Update structured resume data

### AI Project Description

User provides:

> Built a payment system using Spring Boot and Cashfree.

AI can generate concise resume bullet points.

### AI Resume Improvement

Analyze:

- Grammar
- Clarity
- Impact
- Technical terminology
- Redundant content
- Weak bullet points

### Job Description Matching

User uploads a JD.

System compares:

```text
Resume
+
Job Description
```

and identifies:

- Matching skills
- Missing skills
- Relevant projects
- Missing keywords
- Potential improvements

---

# 12. Future ResumeFit Integration

ResumeFlow can eventually become the resume-management foundation for ResumeFit.

Architecture:

```text
                    Resume Platform
                          │
             ┌────────────┴────────────┐
             │                         │
       ResumeFlow                  ResumeFit
             │                         │
       Resume Management          JD Analysis
       Resume Editing             Gap Analysis
       Version History             Optimization
       Templates                   Tailoring
             │                         │
             └────────────┬────────────┘
                          ↓
                  Resume Generator
                          ↓
                         PDF
```

ResumeFlow maintains the user's master resume.

ResumeFit creates job-specific optimized versions.

---

# 13. Technology Stack

## Frontend

- Next.js
- TypeScript
- React
- Tailwind CSS

## Backend

- Java
- Spring Boot
- Spring Security
- Spring Data JPA
- Hibernate
- Maven
- REST API
- JWT authentication

## Database

- PostgreSQL

## Resume Generation

- LaTeX
- PDF generation

## Storage

Initially local development storage can be used.

Production architecture should be designed so object storage such as S3-compatible storage can be introduced later.

---

# 14. Frontend Architecture

Suggested structure:

```text
frontend/
├── app/
├── components/
│   ├── ui/
│   ├── resume/
│   ├── editor/
│   ├── preview/
│   └── dashboard/
├── hooks/
├── lib/
├── services/
├── types/
└── utils/
```

The frontend should communicate with the Spring Boot backend through REST APIs.

---

# 15. Backend Architecture

Suggested structure:

```text
backend/
└── src/main/java/
    └── com/resumeflow/
        ├── auth/
        ├── user/
        ├── resume/
        ├── template/
        ├── document/
        ├── pdf/
        ├── version/
        ├── storage/
        ├── common/
        └── config/
```

Use a layered architecture:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
Database
```

Keep business logic out of controllers.

---

# 16. Initial Database Entities

### User

- id
- name
- email
- passwordHash
- createdAt
- updatedAt

### Resume

- id
- userId
- name
- templateId
- resumeData
- createdAt
- updatedAt

### ResumeTemplate

- id
- name
- description
- templateType
- templateContent
- createdAt

### ResumeVersion

- id
- resumeId
- versionNumber
- resumeData
- createdAt

The exact database schema can be refined during implementation.

---

# 17. UI Pages

## 1. Landing Page

Explain:

> Update your resume without touching LaTeX.

CTA:

> Create Your Resume

## 2. Authentication

- Login
- Signup

## 3. Dashboard

Display:

- Resumes
- Recent updates
- Templates
- Quick actions

Example:

```text
My Resumes

Backend Developer Resume
Last updated: Today

[Edit] [Preview] [Download]
```

## 4. Resume Editor

Main workspace:

```text
┌─────────────────────────────────────────────┐
│ Resume Name             Save   Download PDF │
├──────────────────────┬──────────────────────┤
│ Editor               │ Preview              │
│                      │                      │
│ Personal Information │                      │
│ Experience           │      Resume          │
│ Projects             │      Preview         │
│ Skills               │                      │
│ Education            │                      │
│                      │                      │
└──────────────────────┴──────────────────────┘
```

## 5. Template Selection

Users can select a resume template.

## 6. Version History

Display previous resume versions.

---

# 18. UX Principles

The product should be:

- Fast
- Minimal
- Professional
- Developer-friendly
- Desktop-first
- Responsive
- Easy to understand

Avoid unnecessary complexity.

The main workflow should always remain obvious:

```text
Edit → Save → Preview → Download
```

---

# 19. Important Product Principle

Do not build ResumeFlow as a generic Word/Google Docs clone.

The application should understand that a resume consists of structured entities.

For example:

```text
Project
 ├── Name
 ├── Description
 ├── Technologies
 ├── Links
 └── Dates
```

rather than treating the entire project as arbitrary text.

This allows future features such as:

- AI optimization
- JD matching
- ATS analysis
- Version comparison
- Skill tracking
- Resume tailoring

---

# 20. Development Strategy

Build incrementally.

### Phase 1 — Foundation

- Next.js
- TypeScript
- Tailwind CSS
- Spring Boot
- PostgreSQL
- Authentication foundation

### Phase 2 — Resume CRUD

- Create
- Read
- Update
- Delete
- Structured resume data

### Phase 3 — Resume Editor

- Personal information
- Skills
- Experience
- Projects
- Education
- Certifications

### Phase 4 — Template System

- Resume templates
- Template selection
- Structured data → template

### Phase 5 — PDF Generation

- LaTeX generation
- Compilation
- PDF preview
- PDF download

### Phase 6 — Version History

- Version creation
- Version listing
- Restore previous version

### Phase 7 — AI Capabilities

- AI editing
- AI bullet generation
- AI resume improvement

### Phase 8 — ResumeFit Integration

- JD analysis
- Gap analysis
- Resume optimization
- Job-specific resume generation

---

# 21. MVP Success Criteria

The MVP is successful when a user can:

1. Create an account.
2. Create or upload a resume.
3. Enter resume information through structured fields.
4. Select a template.
5. Edit information without touching LaTeX.
6. Preview the resume.
7. Save changes.
8. Generate a PDF.
9. Download the updated resume.

The core experience should feel significantly faster than manually editing and compiling a LaTeX resume.
