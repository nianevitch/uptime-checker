import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { User, UserService } from './user.service';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-user-profile',
  templateUrl: './user-profile.component.html',
  styleUrls: ['./user-profile.component.css']
})
export class UserProfileComponent implements OnInit {
  @Output() close = new EventEmitter<void>();
  user: User | null = null;
  isLoading = false;
  error: string | null = null;
  success: string | null = null;
  isEditing = false;

  form = this.fb.group({
    email: ['', [Validators.required, Validators.email]]
  });

  constructor(
    private readonly fb: FormBuilder,
    private readonly userService: UserService,
    private readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    this.loadUser();
  }

  loadUser(): void {
    this.isLoading = true;
    this.error = null;
    this.userService.getCurrentUser().subscribe({
      next: user => {
        this.user = user;
        this.form.patchValue({
          email: user.email
        });
        this.isLoading = false;
      },
      error: err => {
        this.error = err.error?.error ?? 'Failed to load user profile';
        this.isLoading = false;
      }
    });
  }

  startEdit(): void {
    this.isEditing = true;
    this.error = null;
    this.success = null;
  }

  cancelEdit(): void {
    this.isEditing = false;
    if (this.user) {
      this.form.patchValue({
        email: this.user.email
      });
    }
    this.error = null;
    this.success = null;
  }

  submit(): void {
    if (this.form.invalid || !this.user) {
      this.form.markAllAsTouched();
      return;
    }

    const userId = this.authService.getCurrentUserId();
    if (!userId) {
      this.error = 'Not authenticated';
      return;
    }

    const payload = {
      email: this.form.value.email ?? undefined
    };

    this.userService.updateUser(userId, payload).subscribe({
      next: updated => {
        this.user = updated;
        this.isEditing = false;
        this.success = 'Profile updated successfully';
        this.error = null;
        // Update stored email
        const stored = localStorage.getItem('uptime_user');
        if (stored) {
          const user = JSON.parse(stored);
          user.email = updated.email;
          localStorage.setItem('uptime_user', JSON.stringify(user));
        }
      },
      error: err => {
        this.error = err.error?.error ?? 'Failed to update profile';
      }
    });
  }

  get userInitials(): string {
    if (!this.user) return 'U';
    const email = this.user.email;
    const parts = email.split('@')[0].split('.');
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return email.substring(0, 2).toUpperCase();
  }

  closeModal(): void {
    this.close.emit();
  }
}

