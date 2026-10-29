import { Component, EventEmitter, Input, OnChanges, Output, inject } from '@angular/core';
import { Api } from './api';

/** Upload button with a preview. Emits the stored image key, or null when removed. */
@Component({
  selector: 'image-picker',
  standalone: true,
  template: `
    <div class="picker">
      <span class="lbl">{{ label }}</span>
      @if (src) { <img [src]="src" alt="Preview" /> } @else { <em>No image</em> }
      <input type="file" accept="image/*" (change)="pick($event)" />
      @if (key) { <button type="button" class="link danger" (click)="keyChange.emit(null)">Remove</button> }
      @if (error) { <span class="error">{{ error }}</span> }
    </div>
  `,
})
export class ImagePickerComponent implements OnChanges {
  private api = inject(Api);
  @Input() label = '';
  @Input() key: string | null | undefined;
  @Output() keyChange = new EventEmitter<string | null>();
  src = '';
  error = '';

  ngOnChanges() {
    if (this.key) this.api.image(this.key).subscribe({ next: b => (this.src = URL.createObjectURL(b)), error: () => (this.src = '') });
    else this.src = '';
  }

  pick(ev: Event) {
    const f = (ev.target as HTMLInputElement).files?.[0];
    if (!f) return;
    this.error = '';
    this.api.upload(f).subscribe({
      next: r => this.keyChange.emit(r.key),
      error: () => (this.error = 'Upload failed. Use a JPG or PNG under 20 MB.'),
    });
  }
}
