package com.sapphireai.model.enums;

/**
 * Document type classification for chunking strategy selection.
 * AUTO-DETECTED using multi-signal analysis (structure, content, filename).
 */
public enum DocumentType {
    POLICY,        // Corporate policies, procedures, HR docs
    TECHNICAL,     // Technical manuals, how-tos, guides
    LEGAL,         // Legal documents, contracts
    CODE,          // Source code files
    SPREADSHEET,   // Excel, CSV with tables
    PRESENTATION,  // PowerPoint slides
    EMAIL,         // Email threads
    CONFLUENCE,    // Confluence wiki pages
    SHAREPOINT,    // SharePoint documents
    GENERAL        // Default fallback for everything else
}