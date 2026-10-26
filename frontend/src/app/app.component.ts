import { Component, OnInit, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './auth';
import { BrandService } from './brand';
import { IconComponent } from './icon.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  template: `
    @if (!auth.loggedIn) {
      <router-outlet />
    } @else {
      <div class="shell" [class.collapsed]="collapsed">
        <aside class="side">
          <div class="brand"><span class="mark">QS</span><span class="brand-name">Quote SaaS</span></div>
          <nav class="nav">
            <a routerLink="/" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: true }"><app-icon name="dashboard" /><span>Dashboard</span></a>
            <a routerLink="/statements" routerLinkActive="on"><app-icon name="file" /><span>Statements</span></a>
            <a routerLink="/quote/new" routerLinkActive="on"><app-icon name="plus" /><span>New statement</span></a>
            <a routerLink="/settings" routerLinkActive="on"><app-icon name="settings" /><span>Settings</span></a>
          </nav>
        </aside>

        <div class="main">
          <header class="top">
            <button class="icon-btn" (click)="collapsed = !collapsed" aria-label="Toggle menu"><app-icon name="menu" /></button>
            <div class="company">
              @if (brand.logo()) { <img [src]="brand.logo()" alt="Company logo" /> }
              <strong>{{ brand.name() }}</strong>
              <span class="divider"></span>
              <span class="page">{{ title }}</span>
            </div>
            <span class="spacer"></span>
            <button class="icon-btn" (click)="toggleTheme()" aria-label="Toggle dark mode"><app-icon [name]="dark ? 'sun' : 'moon'" /></button>
            <div class="usermenu">
              <button class="user" (click)="menuOpen = !menuOpen">
                <span class="avatar">{{ initials }}</span>
                <span class="uname">{{ brand.name() }}</span>
                <app-icon name="chevron" [size]="16" />
              </button>
              @if (menuOpen) {
                <div class="backdrop" (click)="menuOpen = false"></div>
                <div class="dropdown">
                  <div class="who"><b>{{ brand.name() }}</b><small>{{ brand.email() }}</small></div>
                  <a routerLink="/settings"><app-icon name="settings" [size]="18" /> Settings</a>
                  <button class="out" (click)="auth.logout()"><app-icon name="logout" [size]="18" /> Sign out</button>
                </div>
              }
            </div>
          </header>
          <main><router-outlet /></main>
          <footer>&copy; {{ year }} {{ brand.name() }}. All rights reserved.</footer>
        </div>
      </div>
    }
  `,
})
export class AppComponent implements OnInit {
  auth = inject(AuthService);
  brand = inject(BrandService);
  private router = inject(Router);
  collapsed = window.innerWidth < 860;
  menuOpen = false;
  dark = localStorage.getItem('theme') === 'dark';
  year = new Date().getFullYear();

  constructor() { this.applyTheme(); }

  ngOnInit() {
    if (this.auth.loggedIn) this.brand.refresh();
    this.router.events.subscribe(e => {
      if (e instanceof NavigationEnd) { this.menuOpen = false; if (window.innerWidth < 860) this.collapsed = true; }
    });
  }

  get title() {
    const u = this.router.url;
    if (u === '/' || u === '') return 'Dashboard';
    if (u.startsWith('/statements')) return 'Statements';
    if (u.startsWith('/quote')) return 'Statement';
    if (u.startsWith('/settings')) return 'Settings';
    return '';
  }

  get initials() {
    const n = this.brand.name() || this.brand.email() || '?';
    return n.split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();
  }

  toggleTheme() {
    this.dark = !this.dark;
    localStorage.setItem('theme', this.dark ? 'dark' : 'light');
    this.applyTheme();
  }

  private applyTheme() { document.documentElement.setAttribute('data-theme', this.dark ? 'dark' : 'light'); }
}
