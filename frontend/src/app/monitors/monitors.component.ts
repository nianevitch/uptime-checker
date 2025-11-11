import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Monitor, MonitorService } from './monitor.service';
import { AuthService } from '../auth/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-monitors',
  templateUrl: './monitors.component.html',
  styleUrls: ['./monitors.component.css']
})
export class MonitorsComponent implements OnInit {
  monitors: Monitor[] = [];
  error: string | null = null;
  success: string | null = null;
  isLoading = false;
  selectedMonitor: Monitor | null = null;

  form = this.fb.group({
    url: ['', [Validators.required]],
    label: [''],
    frequencyMinutes: [5, [Validators.required, Validators.min(1), Validators.max(1440)]]
  });

  constructor(
    private readonly fb: FormBuilder,
    private readonly monitorService: MonitorService,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    if (!this.authService.isAuthenticated()) {
      this.router.navigate(['login']);
      return;
    }
    this.loadMonitors();
  }

  loadMonitors(): void {
    this.isLoading = true;
    this.monitorService.list().subscribe({
      next: data => {
        this.monitors = data;
        this.isLoading = false;
      },
      error: err => {
        this.error = err.error?.error ?? 'Failed to load monitors';
        this.isLoading = false;
      }
    });
  }

  startCreate(): void {
    this.selectedMonitor = null;
    this.form.reset({
      url: '',
      label: '',
      frequencyMinutes: 5
    });
  }

  editMonitor(monitor: Monitor): void {
    this.selectedMonitor = monitor;
    this.form.reset({
      url: monitor.url,
      label: monitor.label ?? '',
      frequencyMinutes: monitor.frequencyMinutes
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const payload = this.form.value as { url: string; label?: string; frequencyMinutes: number };
    const request = this.selectedMonitor
      ? this.monitorService.update(this.selectedMonitor.id, payload)
      : this.monitorService.create(payload);
    request.subscribe({
      next: () => {
        this.success = this.selectedMonitor ? 'Monitor updated.' : 'Monitor created.';
        this.error = null;
        this.startCreate();
        this.loadMonitors();
      },
      error: err => {
        this.error = err.error?.error ?? 'Unable to save monitor.';
      }
    });
  }

  deleteMonitor(id: number): void {
    if (!confirm('Delete this monitor?')) {
      return;
    }
    this.monitorService.delete(id).subscribe({
      next: () => {
        this.success = 'Monitor deleted.';
        this.loadMonitors();
      },
      error: err => {
        this.error = err.error?.error ?? 'Unable to delete monitor.';
      }
    });
  }

  runCheck(id: number): void {
    this.monitorService.execute(id).subscribe({
      next: () => {
        this.success = 'Check executed.';
        this.loadMonitors();
      },
      error: err => {
        this.error = err.error?.error ?? 'Failed to execute check.';
      }
    });
  }

}

