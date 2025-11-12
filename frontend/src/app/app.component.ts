import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth/auth.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  showProfileModal = false;

  constructor(
    private readonly router: Router,
    private readonly authService: AuthService
  ) {}

  navigate(path: string): void {
    this.router.navigate([path]);
  }

  get isAuthenticated(): boolean {
    return this.authService.isAuthenticated();
  }

  get userInitials(): string {
    const email = this.authService.getCurrentUserEmail();
    if (!email) return 'U';
    const parts = email.split('@')[0].split('.');
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return email.substring(0, 2).toUpperCase();
  }

  openProfile(): void {
    this.showProfileModal = true;
  }

  closeProfile(): void {
    this.showProfileModal = false;
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => this.router.navigate(['login']),
      error: () => this.router.navigate(['login'])
    });
  }
}

