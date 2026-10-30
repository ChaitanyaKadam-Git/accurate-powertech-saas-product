import { Component, EventEmitter, Input, Output } from '@angular/core';
import { ModuleInfo } from './api';

const RIGHTS = ['VIEW', 'CREATE', 'EDIT', 'DELETE', 'DOWNLOAD', 'SEND'];

/**
 * Module x right grid. Used for roles (plain checkboxes) and for one person
 * (pass `base` = the role's rights so grants show green and revokes show red).
 */
@Component({
  selector: 'rights-matrix',
  standalone: true,
  template: `
    @if (base) {
      <div class="legend">
        <span><i style="background:rgba(21,128,61,.35)"></i>Granted on top of the role</span>
        <span><i style="background:rgba(220,38,38,.35)"></i>Revoked from the role</span>
      </div>
    }
    <div class="scroll">
      <table class="matrix">
        <thead>
          <tr><th class="sr">Sr.</th><th>Menu</th>@for (r of rights; track r) { <th>{{ label(r) }}</th> }<th>All</th></tr>
        </thead>
        <tbody>
          @for (g of groups; track g.name) {
            <tr class="grp"><td [attr.colspan]="rights.length + 3">{{ g.name }}</td></tr>
            @for (m of g.mods; track m.key) {
              <tr [class.locked]="m.adminOnly">
                <td class="sr">{{ sr(m) }}</td>
                <td class="nowrap">{{ m.label }} @if (m.adminOnly) { <span class="badge">Admin only</span> } @if (!m.ready) { <span class="badge warn">Coming soon</span> }</td>
                @for (r of rights; track r) {
                  @if (!m.rights.includes(r)) { <td class="na">&ndash;</td> }
                  @else if (m.adminOnly) { <td><input type="checkbox" disabled [checked]="false" /></td> }
                  @else {
                    <td [class.diff-add]="diff(m, r) === 1" [class.diff-rem]="diff(m, r) === -1">
                      <input type="checkbox" [checked]="has(m, r)" [disabled]="readonly" (change)="toggle(m, r)" [attr.aria-label]="m.label + ' ' + r" />
                    </td>
                  }
                }
                <td>@if (!m.adminOnly && !readonly) { <button type="button" class="link" (click)="toggleRow(m)">{{ rowFull(m) ? 'Clear' : 'All' }}</button> }</td>
              </tr>
            }
          }
        </tbody>
      </table>
    </div>
  `,
})
export class RightsMatrixComponent {
  @Input() modules: ModuleInfo[] = [];
  @Input() selected: string[] = [];
  @Input() base: string[] | null = null;
  @Input() readonly = false;
  @Output() selectedChange = new EventEmitter<string[]>();
  rights = RIGHTS;

  label(r: string) { return r.charAt(0) + r.slice(1).toLowerCase(); }
  key(m: ModuleInfo, r: string) { return `${m.key}:${r}`; }
  has(m: ModuleInfo, r: string) { return this.selected.includes(this.key(m, r)); }
  diff(m: ModuleInfo, r: string) {
    if (!this.base) return 0;
    const inBase = this.base.includes(this.key(m, r)), now = this.has(m, r);
    return now && !inBase ? 1 : !now && inBase ? -1 : 0;
  }
  /** Serial number in menu order (1 = first module). */
  sr(m: ModuleInfo) { return this.modules.indexOf(m) + 1; }
  rowFull(m: ModuleInfo) { return m.rights.every(r => this.has(m, r)); }

  get groups() {
    const out: { name: string; mods: ModuleInfo[] }[] = [];
    for (const m of this.modules) {
      let g = out.find(x => x.name === m.group);
      if (!g) { g = { name: m.group, mods: [] }; out.push(g); }
      g.mods.push(m);
    }
    return out;
  }

  toggle(m: ModuleInfo, r: string) {
    const k = this.key(m, r);
    this.selectedChange.emit(this.selected.includes(k) ? this.selected.filter(x => x !== k) : [...this.selected, k]);
  }

  toggleRow(m: ModuleInfo) {
    const keys = m.rights.map(r => this.key(m, r));
    const rest = this.selected.filter(x => !keys.includes(x));
    this.selectedChange.emit(this.rowFull(m) ? rest : [...rest, ...keys]);
  }
}
