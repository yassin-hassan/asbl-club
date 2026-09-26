import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';

// Translation keys for the texts; the dialog closes with true only when the user confirms.
export interface ConfirmDialogData {
  title: string;
  message: string;
  confirm: string;
  cancel: string;
  // Values for placeholders in the texts, e.g. { name: 'Alice' } for "Exclude {{ name }}?".
  params?: Record<string, string>;
}

// Reusable "are you sure?" dialog for irreversible actions.
@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [MatDialogModule, MatButtonModule, TranslocoPipe],
  template: `
    <h2 mat-dialog-title>{{ data.title | transloco: data.params }}</h2>
    <mat-dialog-content>{{ data.message | transloco: data.params }}</mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false">{{ data.cancel | transloco }}</button>
      <button mat-flat-button type="button" class="danger" [mat-dialog-close]="true">{{ data.confirm | transloco }}</button>
    </mat-dialog-actions>
  `,
  styles: '.danger { --mat-button-filled-container-color: var(--mat-sys-error); --mat-button-filled-label-text-color: var(--mat-sys-on-error); }',
})
export class ConfirmDialog {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
