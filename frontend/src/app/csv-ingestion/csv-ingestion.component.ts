import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { CsvIngestionService, CsvFileSummary, CsvIngestionResponse, CsvFileData } from './csv-ingestion.service';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-csv-ingestion',
  templateUrl: './csv-ingestion.component.html',
  styleUrls: ['./csv-ingestion.component.css']
})
export class CsvIngestionComponent implements OnInit, OnDestroy {
  Math = Math; // Expose Math to template
  files: CsvFileSummary[] = [];
  error: string | null = null;
  success: string | null = null;
  isLoading = false;
  showUploadModal = false;
  selectedFiles: File[] = [];
  isUploading = false;
  uploadProgress: Map<string, boolean> = new Map();
  isDragOver = false;
  isDeleting: string | null = null;
  
  // Data grid properties
  showDataModal = false;
  fileData: CsvFileData | null = null;
  isLoadingData = false;
  currentPage = 1;
  pageSize = 25;
  pageSizeOptions = [10, 25, 50, 100];
  sortColumn: string | null = null;
  sortDirection: 'asc' | 'desc' = 'asc';
  sortedRows: { [key: string]: string }[] = [];
  private dataLoadSubscription: Subscription | null = null; // Track active data load request
  
  // Merge modal properties
  showMergeModal = false;
  duplicateRows: { index: number; row: { [key: string]: string } }[] = [];
  originalDuplicateRows: { index: number; row: { [key: string]: string } }[] = []; // Original values for change tracking
  mergedRow: { [key: string]: string } = {};
  isMerging = false;
  hasUnsavedChanges = false; // Track if there are unsaved changes
  isSaving = false; // Track if save is in progress
  skipIdenticalFields = true; // Setting to skip identical fields
  identicalFields: Set<string> = new Set(); // Fields that are identical across all rows
  identityField: string | null = null; // The identity field (e.g., email)
  showDuplicateResolvedDialog = false; // Show dialog when duplicates are resolved
  savingFields: Map<string, boolean> = new Map(); // Track which fields are being saved (key: "rowIndex-header")
  resolvedDuplicateIndices: number[] = []; // Indices of rows that are no longer duplicates
  showDeduplicatePrompt = false; // Show prompt to mark rows as deduplicated
  initialDuplicateRowCount = 0; // Track initial number of rows when merge modal was opened
  
  // Duplicate groups pagination
  duplicateGroupsPage = 1;
  duplicateGroupsPageSize = 10;
  duplicateGroupsPageSizeOptions = [5, 10, 20, 50];
  
  // Merge strategy (for bulk auto-merge operations only; manual merge is always used in side-by-side modal)
  mergeStrategy: 'merge-delete' | 'auto-merge' = 'merge-delete';
  isAutoMerging = false;

  constructor(
    private readonly csvIngestionService: CsvIngestionService,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['login']);
      return;
    }
    this.loadFiles();
  }

  loadFiles(): void {
    this.isLoading = true;
    this.error = null;
    this.csvIngestionService.list().subscribe({
      next: data => {
        this.files = data;
        this.isLoading = false;
      },
      error: err => {
        this.error = err.error?.message || 'Failed to load CSV files';
        this.isLoading = false;
      }
    });
  }

  openUploadModal(): void {
    this.selectedFiles = [];
    this.error = null;
    this.success = null;
    this.showUploadModal = true;
  }

  closeUploadModal(): void {
    this.showUploadModal = false;
    this.selectedFiles = [];
    this.uploadProgress.clear();
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.processFiles(Array.from(input.files));
      // Reset the input so the same file can be selected again
      input.value = '';
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;

    if (event.dataTransfer?.files) {
      const fileArray = Array.from(event.dataTransfer.files);
      this.processFiles(fileArray);
    }
  }

  private processFiles(fileArray: File[]): void {
    // Filter only CSV files
    const csvFiles = fileArray.filter(file => 
      file.name.toLowerCase().endsWith('.csv')
    );
    
    if (csvFiles.length !== fileArray.length) {
      this.error = 'Some files were skipped. Only CSV files are allowed.';
    }
    
    // Add to selected files (avoid duplicates)
    csvFiles.forEach(file => {
      if (!this.selectedFiles.find(f => f.name === file.name && f.size === file.size)) {
        this.selectedFiles.push(file);
      }
    });
  }

  removeFile(index: number): void {
    this.selectedFiles.splice(index, 1);
  }

  uploadFiles(): void {
    if (this.selectedFiles.length === 0) {
      this.error = 'Please select at least one CSV file to upload';
      return;
    }

    this.isUploading = true;
    this.error = null;
    this.success = null;
    this.uploadProgress.clear();

    // Initialize progress tracking
    this.selectedFiles.forEach(file => {
      this.uploadProgress.set(file.name, false);
    });

    // Upload files sequentially to avoid overwhelming the server
    this.uploadNext(0);
  }

  private uploadNext(index: number): void {
    if (index >= this.selectedFiles.length) {
      // All uploads completed
      this.isUploading = false;
      this.loadFiles(); // Refresh the list
      const successCount = Array.from(this.uploadProgress.values())
        .filter(completed => completed).length;
      if (successCount === this.selectedFiles.length) {
        this.success = `Successfully uploaded ${successCount} file(s)`;
        this.closeUploadModal();
      } else {
        const failedCount = this.selectedFiles.length - successCount;
        this.error = `${failedCount} file(s) failed to upload. ${successCount} succeeded.`;
      }
      return;
    }

    const file = this.selectedFiles[index];
    this.csvIngestionService.upload(file).subscribe({
      next: response => {
        if (response.success) {
          this.uploadProgress.set(file.name, true);
        } else {
          this.uploadProgress.set(file.name, false);
          this.error = this.error 
            ? `${this.error}\n${file.name}: ${response.message}`
            : `${file.name}: ${response.message}`;
        }
        // Upload next file
        this.uploadNext(index + 1);
      },
      error: err => {
        this.uploadProgress.set(file.name, false);
        const errorMsg = err.error?.message || `Failed to upload ${file.name}`;
        this.error = this.error 
          ? `${this.error}\n${file.name}: ${errorMsg}`
          : `${file.name}: ${errorMsg}`;
        // Continue with next file even if this one failed
        this.uploadNext(index + 1);
      }
    });
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleString();
  }

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
  }

  deleteFile(id: string, fileName: string): void {
    if (!confirm(`Are you sure you want to delete "${fileName}"? This action cannot be undone.`)) {
      return;
    }

    this.isDeleting = id;
    this.error = null;
    this.success = null;

    // Close data modal if it's open for this file
    if (this.showDataModal && this.fileData && this.fileData.id === id) {
      this.closeDataModal();
    }

    // Cancel auto-merge if it's running for this file
    if (this.isAutoMerging && this.fileData && this.fileData.id === id) {
      this.isAutoMerging = false;
    }

    // Cancel any pending data load request for this file
    if (this.dataLoadSubscription && this.isLoadingData) {
      this.dataLoadSubscription.unsubscribe();
      this.dataLoadSubscription = null;
      this.isLoadingData = false;
    }

    this.csvIngestionService.delete(id).subscribe({
      next: () => {
        this.success = `File "${fileName}" deleted successfully`;
        this.isDeleting = null;
        this.loadFiles(); // Refresh the list
      },
      error: err => {
        this.error = err.error?.message || `Failed to delete "${fileName}"`;
        this.isDeleting = null;
      }
    });
  }

  viewFileData(id: string): void {
    // Cancel any existing data load request
    if (this.dataLoadSubscription) {
      this.dataLoadSubscription.unsubscribe();
      this.dataLoadSubscription = null;
    }
    
    this.isLoadingData = true;
    this.error = null;
    this.currentPage = 1;
    this.sortColumn = null;
    this.sortDirection = 'asc';
    
    console.log('Loading file data for ID:', id); // Debug log
    this.dataLoadSubscription = this.csvIngestionService.getFileData(id).subscribe({
      next: data => {
        console.log('File data loaded:', id); // Debug log
        // Check if request was cancelled (e.g., file was deleted)
        if (!this.isLoadingData || !this.dataLoadSubscription) {
          return;
        }
        this.fileData = data;
        this.applySorting();
        this.isLoadingData = false;
        this.showDataModal = true;
        this.dataLoadSubscription = null;
      },
      error: err => {
        console.error('Error loading file data:', err); // Debug log
        this.error = err.error?.message || 'Failed to load file data';
        this.isLoadingData = false;
        this.dataLoadSubscription = null;
        // If file not found, close modal if it was open
        if (err.status === 404 && this.showDataModal) {
          this.closeDataModal();
        }
      }
    });
  }

  closeDataModal(): void {
    // Cancel any pending data load request
    if (this.dataLoadSubscription) {
      this.dataLoadSubscription.unsubscribe();
      this.dataLoadSubscription = null;
    }
    this.showDataModal = false;
    this.fileData = null;
    this.sortedRows = [];
    this.isLoadingData = false;
  }

  getHeaders(): string[] {
    if (!this.fileData) return [];
    // Exclude _status from display headers
    return this.fileData.headers.filter(h => h !== '_status');
  }

  getPaginatedRows(): { [key: string]: string }[] {
    if (!this.sortedRows.length) return [];
    const start = (this.currentPage - 1) * this.pageSize;
    const end = start + this.pageSize;
    return this.sortedRows.slice(start, end);
  }

  getTotalPages(): number {
    if (!this.sortedRows.length) return 0;
    return Math.ceil(this.sortedRows.length / this.pageSize);
  }

  getTotalRows(): number {
    return this.sortedRows.length;
  }

  onPageChange(page: number): void {
    this.currentPage = page;
  }

  onPageSizeChange(size: number): void {
    this.pageSize = size;
    this.currentPage = 1;
  }

  sort(column: string): void {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
    this.applySorting();
    this.currentPage = 1;
  }

  private applySorting(): void {
    if (!this.fileData) {
      this.sortedRows = [];
      return;
    }

    this.sortedRows = [...this.fileData.rows];

    if (this.sortColumn) {
      this.sortedRows.sort((a, b) => {
        const aVal = a[this.sortColumn!] || '';
        const bVal = b[this.sortColumn!] || '';
        
        const comparison = aVal.localeCompare(bVal, undefined, { 
          numeric: true, 
          sensitivity: 'base' 
        });
        
        return this.sortDirection === 'asc' ? comparison : -comparison;
      });
    }
  }

  /**
   * Efficiently update sortedRows after a merge operation.
   * Only updates the affected rows instead of recreating the entire array.
   */
  private updateSortedRowsAfterMerge(
    deletedIndices: number[],
    mergedRowIndex: number,
    mergedRowData: { [key: string]: string } | null,
    affectedRows?: { [key: string]: { [key: string]: string } }
  ): void {
    if (!this.fileData) {
      return;
    }

    // Create a map of deleted rows by their identity (email) for quick lookup
    const deletedRowsMap = new Map<string, { [key: string]: string }>();
    deletedIndices.forEach(idx => {
      const deletedRow = this.fileData!.rows[idx];
      if (deletedRow) {
        const identity = this.findEmailInRow(deletedRow);
        if (identity) {
          deletedRowsMap.set(identity, deletedRow);
        }
      }
    });

    // Remove deleted rows from sortedRows by matching identity
    this.sortedRows = this.sortedRows.filter(row => {
      const rowIdentity = this.findEmailInRow(row);
      if (rowIdentity && deletedRowsMap.has(rowIdentity)) {
        // Check if it's the exact same row (same reference or same data)
        const deletedRow = deletedRowsMap.get(rowIdentity)!;
        return row !== deletedRow && JSON.stringify(row) !== JSON.stringify(deletedRow);
      }
      return true;
    });

    // Insert merged row in the correct sorted position if we're sorting and merged row exists
    if (mergedRowData && mergedRowIndex >= 0) {
      if (this.sortColumn) {
        const mergedValue = mergedRowData[this.sortColumn] || '';
        let insertIndex = this.sortedRows.length;
        
        for (let i = 0; i < this.sortedRows.length; i++) {
          const rowValue = this.sortedRows[i][this.sortColumn] || '';
          const comparison = mergedValue.localeCompare(rowValue, undefined, { 
            numeric: true, 
            sensitivity: 'base' 
          });
          
          if ((this.sortDirection === 'asc' && comparison <= 0) || 
              (this.sortDirection === 'desc' && comparison >= 0)) {
            insertIndex = i;
            break;
          }
        }
        
        this.sortedRows.splice(insertIndex, 0, mergedRowData);
      } else {
        // No sorting - insert at the same position as in fileData
        this.sortedRows.splice(mergedRowIndex, 0, mergedRowData);
      }
    }

    // Update affected rows in sortedRows (rows that shifted indices) - only if they changed status
    if (affectedRows && this.fileData) {
      const fileData = this.fileData; // Store in local variable for type narrowing
      Object.keys(affectedRows).forEach(indexStr => {
        const affectedIndex = parseInt(indexStr, 10);
        const affectedRow = affectedRows[indexStr];
        
        if (affectedIndex !== mergedRowIndex) {
          // Find the row in sortedRows by matching with fileData row
          const rowInFileData = fileData.rows[affectedIndex];
          if (rowInFileData) {
            const sortedIndex = this.sortedRows.findIndex(row => row === rowInFileData);
            if (sortedIndex >= 0) {
              // Only update the status field if it changed
              if (this.sortedRows[sortedIndex]['_status'] !== affectedRow['_status']) {
                this.sortedRows[sortedIndex]['_status'] = affectedRow['_status'];
              }
            }
          }
        }
      });
    }
  }

  getSortIcon(column: string): string {
    if (this.sortColumn !== column) {
      return '⇅';
    }
    return this.sortDirection === 'asc' ? '↑' : '↓';
  }

  previousPage(): void {
    if (this.currentPage > 1) {
      this.currentPage--;
    }
  }

  nextPage(): void {
    if (this.currentPage < this.getTotalPages()) {
      this.currentPage++;
    }
  }

  getRowStatus(row: { [key: string]: string }): string {
    return row['_status'] || 'pending';
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'processed':
        return 'status-processed';
      case 'duplicate':
        return 'status-duplicate';
      case 'pending':
        return 'status-pending';
      default:
        return 'status-pending';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'processed':
        return 'Processed';
      case 'duplicate':
        return 'Duplicate';
      case 'pending':
        return 'Pending';
      default:
        return 'Pending';
    }
  }

  openMergeModal(duplicateIndices: number[]): void {
    if (!this.fileData || duplicateIndices.length < 2) {
      return;
    }

    // Get the rows using the provided indices (these are original indices in fileData.rows)
    const duplicateRowsData: { index: number; row: { [key: string]: string } }[] = [];

    duplicateIndices.forEach(originalIndex => {
      if (originalIndex >= 0 && originalIndex < this.fileData!.rows.length) {
        const row = this.fileData!.rows[originalIndex];
        duplicateRowsData.push({ index: originalIndex, row: { ...row } });
      }
    });

    if (duplicateRowsData.length < 2) {
      this.error = 'At least 2 rows are required for merging';
      return;
    }

    this.duplicateRows = duplicateRowsData;
    // Store original values for change tracking (deep copy)
    this.originalDuplicateRows = duplicateRowsData.map(dr => ({
      index: dr.index,
      row: { ...dr.row }
    }));
    this.hasUnsavedChanges = false;
    this.isSaving = false;
    this.showDuplicateResolvedDialog = false;
    this.initialDuplicateRowCount = duplicateRowsData.length; // Track initial count
    
    // Detect identity field
    this.detectIdentityField();
    
    // Detect identical fields
    this.detectIdenticalFields();
    
    // Initialize merged row
    this.initializeMergedRow();
    
    // Side-by-side merge modal always uses manual merge strategy
    // (mergeStrategy property is only for bulk auto-merge operations)
    
    this.showMergeModal = true;
  }

  detectIdentityField(): void {
    this.identityField = null;

    if (this.duplicateRows.length === 0) {
      return;
    }

    // Check for common email field names
    const emailFields = ['email', 'e-mail', 'emailaddress', 'email_address', 'mail', 'emailid', 'email_id', 'contactemail', 'contact_email'];
    const headers = this.getHeaders();

    for (const header of headers) {
      const headerLower = header.toLowerCase();
      for (const emailField of emailFields) {
        if (headerLower === emailField) {
          this.identityField = header;
          return;
        }
      }
    }
  }

  detectIdenticalFields(): void {
    this.identicalFields.clear();
    
    if (this.duplicateRows.length < 2) {
      return;
    }

    const headers = this.getHeaders();
    const firstRow = this.duplicateRows[0].row;

    for (const header of headers) {
      // Exclude identity fields from identical detection
      if (this.isIdentityField(header)) {
        continue;
      }

      const firstValue = (firstRow[header] || '').trim();
      let allIdentical = true;

      for (let i = 1; i < this.duplicateRows.length; i++) {
        const currentValue = (this.duplicateRows[i].row[header] || '').trim();
        if (currentValue !== firstValue) {
          allIdentical = false;
          break;
        }
      }

      // Consider empty values as identical too
      if (allIdentical || (firstValue === '' && this.duplicateRows.every(r => (r.row[header] || '').trim() === ''))) {
        this.identicalFields.add(header);
      }
    }
  }

  isIdentityField(header: string): boolean {
    if (!header) return false;
    const headerLower = header.toLowerCase();
    const identityFields = ['email', 'e-mail', 'emailaddress', 'email_address', 'mail', 'emailid', 'email_id', 'contactemail', 'contact_email'];
    return identityFields.includes(headerLower);
  }

  initializeMergedRow(): void {
    if (this.duplicateRows.length === 0) {
      this.mergedRow = {};
      return;
    }

    // Start with first row
    this.mergedRow = { ...this.duplicateRows[0].row };
    delete this.mergedRow['_status'];

    // If skip identical fields is enabled, auto-populate identical fields
    if (this.skipIdenticalFields) {
      for (const header of this.identicalFields) {
        // Already set from first row, but ensure it's set
        this.mergedRow[header] = this.duplicateRows[0].row[header] || '';
      }
    }

  }

  onSkipIdenticalFieldsChange(): void {
    // Re-initialize merged row when setting changes
    this.initializeMergedRow();
  }


  isFieldIdentical(header: string): boolean {
    return this.identicalFields.has(header);
  }

  shouldShowField(header: string): boolean {
    // Always show identity fields, even if they're identical
    if (this.isIdentityField(header)) {
      return true;
    }
    // If skip identical fields is enabled, hide identical fields
    if (this.skipIdenticalFields) {
      return !this.identicalFields.has(header);
    }
    return true;
  }

  closeMergeModal(): void {
    if (this.hasUnsavedChanges) {
      if (!confirm('You have unsaved changes. Are you sure you want to close?')) {
        return;
      }
    }
    this.showMergeModal = false;
    this.duplicateRows = [];
    this.originalDuplicateRows = [];
    this.mergedRow = {};
    this.hasUnsavedChanges = false;
    this.isSaving = false;
    this.skipIdenticalFields = true;
    this.identicalFields.clear();
    this.identityField = null;
    this.showDuplicateResolvedDialog = false;
    this.showDeduplicatePrompt = false;
    this.resolvedDuplicateIndices = [];
    this.initialDuplicateRowCount = 0;
    this.savingFields.clear();
  }

  getDuplicateGroups(): { identity: string; indices: number[]; rowIds: string[] }[] {
    if (!this.fileData) return [];

    const groups = new Map<string, { indices: number[]; rowIds: string[] }>();
    
    // Use original fileData.rows to get correct indices and IDs
    this.fileData.rows.forEach((row, originalIndex) => {
      if (this.getRowStatus(row) === 'duplicate') {
        // Try to find email for grouping
        const email = this.findEmailInRow(row);
        if (email) {
          if (!groups.has(email)) {
            groups.set(email, { indices: [], rowIds: [] });
          }
          const group = groups.get(email)!;
          group.indices.push(originalIndex);
          // Use row ID if available (more reliable than index)
          if (row['_id']) {
            group.rowIds.push(row['_id']);
          }
        }
      }
    });

    return Array.from(groups.entries()).map(([identity, data]) => ({
      identity,
      indices: data.indices,
      rowIds: data.rowIds
    }));
  }

  getPaginatedDuplicateGroups(): { identity: string; indices: number[] }[] {
    const allGroups = this.getDuplicateGroups();
    const start = (this.duplicateGroupsPage - 1) * this.duplicateGroupsPageSize;
    const end = start + this.duplicateGroupsPageSize;
    return allGroups.slice(start, end);
  }

  getDuplicateGroupsTotalPages(): number {
    const total = this.getDuplicateGroups().length;
    return Math.ceil(total / this.duplicateGroupsPageSize);
  }

  onDuplicateGroupsPageChange(page: number): void {
    this.duplicateGroupsPage = page;
  }

  onDuplicateGroupsPageSizeChange(size: number): void {
    this.duplicateGroupsPageSize = size;
    this.duplicateGroupsPage = 1;
  }

  previousDuplicateGroupsPage(): void {
    if (this.duplicateGroupsPage > 1) {
      this.duplicateGroupsPage--;
    }
  }

  nextDuplicateGroupsPage(): void {
    if (this.duplicateGroupsPage < this.getDuplicateGroupsTotalPages()) {
      this.duplicateGroupsPage++;
    }
  }

  private findEmailInRow(row: { [key: string]: string }): string | null {
    const emailFields = ['email', 'e-mail', 'emailaddress', 'email_address', 'mail', 'emailid', 'email_id', 'contactemail', 'contact_email'];
    for (const field of emailFields) {
      for (const key in row) {
        if (key.toLowerCase() === field && row[key]) {
          return row[key].toLowerCase().trim();
        }
      }
    }
    return null;
  }

  mergeRows(): void {
    if (!this.fileData || this.duplicateRows.length < 2) {
      return;
    }


    this.isMerging = true;
    this.error = null;

    // Get row IDs if available (preferred), otherwise use indices
    const rowIds = this.duplicateRows
      .map(dr => {
        const row = this.fileData!.rows[dr.index];
        return row && row['_id'] ? row['_id'] : null;
      })
      .filter((id): id is string => id !== null);
    
    const rowIndices = this.duplicateRows.map(dr => dr.index).sort((a, b) => b - a);
    const mergedRow = { ...this.mergedRow };

    this.csvIngestionService.mergeRows(this.fileData.id, {
      rowIds: rowIds.length === this.duplicateRows.length ? rowIds : undefined,
      rowIndices: rowIds.length === this.duplicateRows.length ? undefined : rowIndices,
      mergedRow,
      useCustomIdentity: false,
      mergeStrategy: 'manual' // Side-by-side merge modal always uses manual strategy
    }).subscribe({
      next: (response) => {
        // Apply delta: update only the changed rows in fileData and sortedRows
        if (this.fileData) {
          // Remove deleted rows from fileData (in reverse order to maintain indices)
          const sortedIndices = [...rowIndices].sort((a, b) => b - a);
          for (const index of sortedIndices) {
            if (this.fileData.rows[index]) {
              this.fileData.rows.splice(index, 1);
            }
          }
          
          // Insert merged row at the mergedRowIndex in fileData (if merged row was created)
          let mergedRowData: { [key: string]: string } | null = null;
          if (response.mergedRow && response.mergedRowIndex >= 0) {
            mergedRowData = response.mergedRow;
            this.fileData.rows.splice(response.mergedRowIndex, 0, mergedRowData);
          }
          
          // Update any affected rows in fileData (rows that shifted indices)
          if (response.affectedRows && this.fileData) {
            Object.keys(response.affectedRows).forEach(indexStr => {
              const affectedIndex = parseInt(indexStr, 10);
              const affectedRow = response.affectedRows[indexStr];
              // Only update if it's not the merged row (already inserted above)
              if (affectedIndex !== response.mergedRowIndex && this.fileData && this.fileData.rows[affectedIndex]) {
                Object.assign(this.fileData.rows[affectedIndex], affectedRow);
              }
            });
          }
          
          // Update sortedRows efficiently - only update affected rows instead of recreating entire array
          const mergedRowIndex = response.mergedRowIndex >= 0 ? response.mergedRowIndex : -1;
          this.updateSortedRowsAfterMerge(sortedIndices, mergedRowIndex, mergedRowData, response.affectedRows);
        }
        
        this.isMerging = false;
        this.closeMergeModal();
        const strategyMessage = this.mergeStrategy === 'merge-delete' 
          ? 'Duplicate rows removed' 
          : 'Rows merged successfully';
        this.success = strategyMessage;
        setTimeout(() => this.success = '', 3000);
        
        // Reload file list to update row count
        this.loadFiles();
      },
      error: err => {
        this.error = err.error?.message || 'Failed to merge rows';
        this.isMerging = false;
        setTimeout(() => this.error = '', 5000);
      }
    });
  }

  updateMergedField(header: string, value: string): void {
    this.mergedRow[header] = value;
  }

  updateMergedFieldFromRow(header: string, rowIndex: number): void {
    if (rowIndex >= 0 && rowIndex < this.duplicateRows.length) {
      const value = this.duplicateRows[rowIndex].row[header] || '';
      this.updateMergedField(header, value);
    }
  }

  getMergedFieldValue(header: string): string {
    return this.mergedRow[header] || '';
  }

  getRowValue(rowIndex: number, header: string): string {
    if (rowIndex >= 0 && rowIndex < this.duplicateRows.length) {
      return this.duplicateRows[rowIndex].row[header] || '';
    }
    return '';
  }

  isFieldValueIdentical(header: string): boolean {
    if (this.duplicateRows.length < 2) return false;
    
    const firstValue = (this.duplicateRows[0].row[header] || '').trim();
    return this.duplicateRows.every(r => (r.row[header] || '').trim() === firstValue);
  }

  private recalculateTimeout: any = null;

  updateRowValue(rowIndex: number, header: string, value: string): void {
    if (rowIndex >= 0 && rowIndex < this.duplicateRows.length) {
      const oldValue = this.duplicateRows[rowIndex].row[header];
      this.duplicateRows[rowIndex].row[header] = value;
      
      // Update the actual file data row
      const dupRow = this.duplicateRows[rowIndex];
      const originalRow = this.fileData?.rows[dupRow.index];
      if (originalRow) {
        originalRow[header] = value;
      }
      
      // Check if there are unsaved changes
      this.checkForUnsavedChanges();
      
      // Auto-update merged value if it matches the old value
      if (this.mergedRow[header] === oldValue) {
        this.updateMergedField(header, value);
      }
      
      // Don't auto-recalculate on edit - user must save first
    }
  }

  checkForUnsavedChanges(): void {
    if (this.duplicateRows.length !== this.originalDuplicateRows.length) {
      this.hasUnsavedChanges = true;
      return;
    }

    for (let i = 0; i < this.duplicateRows.length; i++) {
      const currentRow = this.duplicateRows[i].row;
      const originalRow = this.originalDuplicateRows[i].row;
      
      for (const key in currentRow) {
        if (currentRow[key] !== originalRow[key]) {
          this.hasUnsavedChanges = true;
          return;
        }
      }
      
      // Also check for new keys
      for (const key in originalRow) {
        if (!(key in currentRow) || currentRow[key] !== originalRow[key]) {
          this.hasUnsavedChanges = true;
          return;
        }
      }
    }
    
    this.hasUnsavedChanges = false;
  }

  saveRowChanges(): void {
    if (!this.fileData || this.isSaving) return;

    this.isSaving = true;
    this.error = null;

    // Update all rows in fileData with changes from duplicate rows
    this.duplicateRows.forEach(dupRow => {
      const originalRow = this.fileData!.rows[dupRow.index];
      if (originalRow) {
        Object.assign(originalRow, dupRow.row);
      }
    });

    // Call backend to update rows and recalculate status
    this.csvIngestionService.updateRowsAndRecalculateStatus(this.fileData.id, this.fileData.rows).subscribe({
      next: (updatedData) => {
        this.fileData = updatedData;
        this.applySorting();
        
        // Check if the rows are still duplicates
        const currentIndices = this.duplicateRows.map(dr => dr.index);
        const duplicateCount = currentIndices.filter(idx => {
          const row = updatedData.rows[idx];
          return row && row['_status'] === 'duplicate';
        }).length;

        // If none of the rows are duplicates anymore, they were resolved
        if (duplicateCount === 0 && currentIndices.length >= 2) {
          // Duplicates were resolved - show dialog
          this.showDuplicateResolvedDialog = true;
        } else {
          // Still duplicates or status changed - reload the modal
          this.originalDuplicateRows = this.duplicateRows.map(dr => ({
            index: dr.index,
            row: { ...dr.row }
          }));
          this.hasUnsavedChanges = false;
          this.openMergeModal(currentIndices);
          this.success = 'Row changes saved successfully';
          setTimeout(() => this.success = '', 3000);
        }
        
        this.isSaving = false;
      },
      error: err => {
        this.error = err.error?.message || 'Failed to save row changes';
        this.isSaving = false;
        setTimeout(() => this.error = '', 5000);
      }
    });
  }

  onDuplicateResolvedContinue(): void {
    this.showDuplicateResolvedDialog = false;
    // Reload the modal to show updated status
    const currentIndices = this.duplicateRows.map(dr => dr.index);
    this.originalDuplicateRows = this.duplicateRows.map(dr => ({
      index: dr.index,
      row: { ...dr.row }
    }));
    this.hasUnsavedChanges = false;
    this.openMergeModal(currentIndices);
  }

  onDuplicateResolvedDone(): void {
    this.showDuplicateResolvedDialog = false;
    this.closeMergeModal();
    this.success = 'Duplicate rows resolved successfully';
    setTimeout(() => this.success = '', 3000);
  }

  closeDeduplicatePrompt(): void {
    this.showDeduplicatePrompt = false;
    this.resolvedDuplicateIndices = [];
  }

  markRowsAsDeduplicated(): void {
    // Rows are already marked as processed by the backend during updateSingleField
    // Just update the local state without reloading the entire file
    this.showDeduplicatePrompt = false;
    const resolvedCount = this.resolvedDuplicateIndices.length;
    const initialCount = this.initialDuplicateRowCount;
    const resolvedIndices = [...this.resolvedDuplicateIndices];
    
    // Update local state: mark resolved rows as processed
    if (this.fileData) {
      resolvedIndices.forEach(index => {
        if (this.fileData && this.fileData.rows[index]) {
          this.fileData.rows[index]['_status'] = 'processed';
        }
      });
      
      // Re-apply sorting to reflect status changes
      this.applySorting();
      
      // If merge modal is open, handle reload logic
      if (this.showMergeModal) {
        // Get current duplicate row indices (excluding resolved ones)
        const currentIndices = this.duplicateRows
          .map(dr => dr.index)
          .filter(idx => !resolvedIndices.includes(idx));
        
        // Check if there are still duplicate rows with the same identity
        const remainingDuplicateIndices = this.findRemainingDuplicates(currentIndices);
        
        if (initialCount === 2 && resolvedCount >= 1) {
          // Started with 2 rows, and at least 1 is resolved - close merge modal and go back to data view
          this.closeMergeModal();
          this.success = `${resolvedCount} row(s) marked as deduplicated. All duplicates resolved.`;
          setTimeout(() => this.success = '', 5000);
        } else if (remainingDuplicateIndices.length >= 2) {
          // Still have 2+ duplicates - reload merge modal with remaining rows
          this.openMergeModal(remainingDuplicateIndices);
          this.success = `${resolvedCount} row(s) marked as deduplicated. Reloaded merge dialog with remaining duplicates.`;
          setTimeout(() => this.success = '', 5000);
        } else {
          // No more duplicates - close merge modal
          this.closeMergeModal();
          this.success = `${resolvedCount} row(s) marked as deduplicated. All duplicates resolved.`;
          setTimeout(() => this.success = '', 5000);
        }
      } else {
        // Merge modal not open - just show success
        this.success = `${resolvedCount} row(s) marked as deduplicated`;
        setTimeout(() => this.success = '', 5000);
      }
    }
    
    this.resolvedDuplicateIndices = [];
  }

  /**
   * Finds remaining duplicate rows that share the same identity as the current duplicate group
   */
  private findRemainingDuplicates(currentIndices: number[]): number[] {
    if (!this.fileData || currentIndices.length === 0) {
      return [];
    }
    
    // Get the identity (email) from the first remaining row
    const firstRow = this.fileData.rows[currentIndices[0]];
    if (!firstRow) {
      return [];
    }
    
    const identityValue = this.findEmailInRow(firstRow);
    if (!identityValue) {
      return currentIndices; // No identity field found, return current indices
    }
    
    // Use getDuplicateGroups to find the group with matching identity
    const duplicateGroups = this.getDuplicateGroups();
    const matchingGroup = duplicateGroups.find(group => group.identity === identityValue);
    
    if (matchingGroup && matchingGroup.indices.length >= 2) {
      return matchingGroup.indices;
    }
    
    return []; // No remaining duplicates found
  }

  getFieldKey(rowIndex: number, header: string): string {
    return `${rowIndex}-${header}`;
  }

  isSavingField(rowIndex: number, header: string): boolean {
    const key = this.getFieldKey(rowIndex, header);
    return this.savingFields.get(key) || false;
  }

  hasFieldChanged(rowIndex: number, header: string): boolean {
    if (rowIndex < 0 || rowIndex >= this.duplicateRows.length || rowIndex >= this.originalDuplicateRows.length) {
      return false;
    }
    const currentValue = this.duplicateRows[rowIndex].row[header] || '';
    const originalValue = this.originalDuplicateRows[rowIndex].row[header] || '';
    return currentValue !== originalValue;
  }

  saveFieldValue(rowIndex: number, header: string): void {
    if (!this.fileData || this.isSavingField(rowIndex, header) || !this.hasFieldChanged(rowIndex, header)) {
      return;
    }

    const key = this.getFieldKey(rowIndex, header);
    this.savingFields.set(key, true);
    this.error = null;

    // Get the actual row index in the file (not the duplicate row index)
    const dupRow = this.duplicateRows[rowIndex];
    const actualRowIndex = dupRow.index;
    const fieldValue = this.duplicateRows[rowIndex].row[header] || '';

    // Call backend to update only this single field
    this.csvIngestionService.updateSingleField(this.fileData.id, actualRowIndex, header, fieldValue).subscribe({
      next: (response) => {
        // Apply delta: update only the changed row(s) in fileData
        if (this.fileData && response.updatedRow) {
          // Update the main row
          const updatedRow = response.updatedRow;
          if (this.fileData.rows[response.updatedRowIndex]) {
            // Merge the updated row data into the existing row
            Object.assign(this.fileData.rows[response.updatedRowIndex], updatedRow);
          }
          
          // Update any affected rows (rows that changed status)
          if (response.affectedRows && this.fileData) {
            Object.keys(response.affectedRows).forEach(indexStr => {
              const affectedIndex = parseInt(indexStr, 10);
              const affectedRow = response.affectedRows[indexStr];
              if (this.fileData && this.fileData.rows[affectedIndex]) {
                Object.assign(this.fileData.rows[affectedIndex], affectedRow);
              }
            });
          }
          
          // Update the duplicate rows display with the updated data
          const updatedRowData = this.fileData.rows[actualRowIndex];
          if (updatedRowData) {
            this.duplicateRows[rowIndex].row[header] = updatedRowData[header] || '';
            // Update original to reflect the saved state
            this.originalDuplicateRows[rowIndex].row[header] = updatedRowData[header] || '';
          }
          
          // Re-apply sorting to reflect any status changes
          this.applySorting();
        }
        
        // Check if duplicates were resolved
        if (response.hasResolvedDuplicates && response.resolvedDuplicateIndices.length > 0) {
          this.resolvedDuplicateIndices = response.resolvedDuplicateIndices;
          this.showDeduplicatePrompt = true;
        }
        
        // Recheck for unsaved changes
        this.checkForUnsavedChanges();
        
        this.savingFields.set(key, false);
        if (!response.hasResolvedDuplicates) {
          this.success = `Field "${header}" saved successfully for row ${rowIndex + 1}`;
          setTimeout(() => this.success = '', 3000);
        }
      },
      error: err => {
        this.error = err.error?.message || `Failed to save field "${header}"`;
        this.savingFields.set(key, false);
        setTimeout(() => this.error = '', 5000);
      }
    });
  }

  useRowForAll(rowIndex: number): void {
    if (rowIndex < 0 || rowIndex >= this.duplicateRows.length) {
      return;
    }

    const sourceRow = this.duplicateRows[rowIndex].row;
    const headers = this.getHeaders();

    for (const header of headers) {
      // Skip if field is identical and skipIdenticalFields is enabled
      if (this.skipIdenticalFields && this.isFieldIdentical(header)) {
        continue;
      }
      this.updateMergedField(header, sourceRow[header] || '');
    }
  }

  autoMergeAllDuplicates(): void {
    if (!this.fileData || this.isAutoMerging) {
      return;
    }

    const duplicateGroups = this.getDuplicateGroups();
    if (duplicateGroups.length === 0) {
      this.success = 'No duplicate groups found';
      setTimeout(() => this.success = '', 3000);
      return;
    }

    if (!confirm(`Are you sure you want to auto-merge all ${duplicateGroups.length} duplicate group(s) using the "${this.mergeStrategy}" strategy?`)) {
      return;
    }

    this.isAutoMerging = true;
    this.error = null;
    this.success = null;

    // Prepare all groups for batch processing
    const groupsToMerge = duplicateGroups.map(group => ({
      rowIds: group.rowIds && group.rowIds.length > 0 ? group.rowIds : undefined,
      rowIndices: (!group.rowIds || group.rowIds.length === 0) && group.indices.length > 0 ? group.indices : undefined
    }));

    // Send all groups to backend in one request
    this.csvIngestionService.autoMergeAllDuplicates(this.fileData.id, groupsToMerge, this.mergeStrategy).subscribe({
      next: (response) => {
        this.isAutoMerging = false;
        
        if (response.failedGroups > 0) {
          this.error = `Auto-merge completed with errors: ${response.successfulGroups} successful, ${response.failedGroups} failed. ${response.errors?.join('; ') || ''}`;
        } else {
          this.success = `Successfully auto-merged ${response.successfulGroups} duplicate group(s)`;
          setTimeout(() => this.success = '', 5000);
        }
        
        // Reload file data to show final state
        if (this.fileData) {
          this.viewFileData(this.fileData.id);
        }
        
        // Reload file list to update row count
        this.loadFiles();
      },
      error: err => {
        this.isAutoMerging = false;
        if (err.status === 404) {
          this.error = 'File was deleted during auto-merge operation';
        } else {
          this.error = `Failed to auto-merge: ${err.error?.message || 'Unknown error'}`;
        }
      }
    });
  }

  // Expose Array to template
  get Array() {
    return Array;
  }

  ngOnDestroy(): void {
    // Clean up subscriptions
    if (this.dataLoadSubscription) {
      this.dataLoadSubscription.unsubscribe();
    }
  }
}


