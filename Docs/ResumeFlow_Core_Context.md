# ResumeFlow — Core Project Context

## 1. Core Idea

ResumeFlow is a simple web application that helps users update an existing resume without manually rebuilding or reformatting it.

The user uploads their existing resume.

The backend analyzes and stores:
- Resume content/details
- Resume sections
- Original template structure
- Formatting information
- Layout information
- Other information necessary to reproduce the resume

After parsing, the user can open the resume in an editor, modify the existing information, save the changes, and generate an updated version of the same resume.

---

## 2. Core Workflow

```text
User uploads existing resume
            ↓
      Backend parses it
            ↓
     Store resume data
            +
     Store original template
            ↓
       Resume Editor
            ↓
     User edits content
            ↓
           Save
            ↓
 Apply changes to original template
            ↓
      Generate resume
            ↓
       Updated Resume
```

---

## 3. Most Important Requirement

### Preserve the User's Original Template

The user's existing resume design/template must be preserved.

The backend must **not automatically redesign or modify the user's resume template**.

The system should only modify the content/details that the user changes.

The following should remain unchanged unless the user explicitly edits them:

- Font
- Font size
- Colors
- Margins
- Spacing
- Section style
- Section order
- Header design
- Footer design
- Icons
- Lines
- Alignment
- Layout
- Overall visual structure
- Other template-specific formatting

### Core Rule

> **User controls the content. The original template remains unchanged.**

---

## 4. Content and Template Separation

The system should conceptually separate:

```text
Resume
   │
   ├── Resume Content
   │      ├── Personal Information
   │      ├── Summary
   │      ├── Skills
   │      ├── Experience
   │      ├── Projects
   │      ├── Education
   │      └── Other Details
   │
   └── Original Template
          ├── Layout
          ├── Styling
          ├── Formatting
          ├── Structure
          └── Other presentation information
```

The user edits the **Resume Content**.

The system preserves the **Original Template**.

Then:

```text
Updated Content
      +
Original Template
      ↓
Updated Resume
```

---

## 5. Example

### Original Resume

```text
Akbarali Nagalpara

SKILLS
Java | Spring Boot | MySQL

PROJECTS
BuySmartAI
Built a product evaluation platform...
```

The user adds:

```text
Docker
Kubernetes
```

The generated resume should become:

```text
Akbarali Nagalpara

SKILLS
Java | Spring Boot | MySQL | Docker | Kubernetes

PROJECTS
BuySmartAI
Built a product evaluation platform...
```

The system should not automatically change the design, spacing, fonts, colors, or layout.

---

## 6. MVP Scope

For the initial version, build only this core functionality:

### Upload

User uploads an existing resume.

### Parse

Backend analyzes the uploaded resume and extracts the content and template/presentation information required for reproduction.

### Store

Store the parsed resume information and original template information.

### Edit

Provide an interface where the user can edit the extracted resume content.

### Generate

Apply the updated content to the original template.

### Download

Allow the user to download the updated resume.

---

## 7. Explicitly Out of Scope for Now

Do NOT implement:

- AI resume optimization
- Job description matching
- ATS scoring
- AI-generated resume content
- Resume recommendations
- Multiple resume variants
- Template marketplace
- Mobile application
- Career tracking
- Job application tracking
- Automatic template redesign
- Automatic styling changes

The first version should focus only on:

```text
Upload
   ↓
Parse
   ↓
Edit
   ↓
Generate
   ↓
Download
```

---

## 8. Technology Direction

The application will be a **web application**.

### Frontend

- Next.js
- TypeScript
- Tailwind CSS

### Backend

- Java
- Spring Boot
- Maven

### Database

- PostgreSQL

The exact implementation approach for parsing and resume generation should be decided based on the uploaded resume format and the requirement to preserve the original template.

---

## 9. Product Principle

ResumeFlow is **not a generic resume builder**.

It is an **existing-resume editing and regeneration platform**.

The key value is:

> **Upload your existing resume once, edit its content easily, and get an updated resume while keeping your original design/template intact.**
