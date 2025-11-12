import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { CheckResult, Monitor, MonitorService } from './monitor.service';
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
  showModal = false;

  readonly form = this.fb.group({
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

  beginCreate(): void {
    this.selectedMonitor = null;
    this.resetForm();
    this.openModal();
  }

  editMonitor(monitor: Monitor): void {
    this.selectedMonitor = monitor;
    this.form.patchValue({
      url: monitor.url ?? '',
      label: monitor.label ?? '',
      frequencyMinutes: monitor.frequencyMinutes ?? 5
    });
    this.openModal();
  }

  closeModal(): void {
    this.showModal = false;
  }

  private openModal(): void {
    this.showModal = true;
  }

  private resetForm(): void {
    this.form.reset({
      url: '',
      label: '',
      frequencyMinutes: 5
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const formValue = this.form.value;
    const payload = {
      url: formValue.url ?? '',
      label: formValue.label ?? undefined,
      frequencyMinutes: formValue.frequencyMinutes ?? 5
    };
    const request = this.selectedMonitor
      ? this.monitorService.update(this.selectedMonitor.id, payload)
      : this.monitorService.create(payload);
    request.subscribe({
      next: monitor => {
        this.success = this.selectedMonitor ? 'Monitor updated.' : 'Monitor created.';
        this.error = null;
        this.upsertMonitor(monitor);
        this.selectedMonitor = null;
        this.closeModal();
        this.resetForm();
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
        this.removeMonitorFromList(id);
      },
      error: err => {
        this.error = err.error?.error ?? 'Unable to delete monitor.';
      }
    });
  }

  runCheck(id: number): void {
    const original = this.monitors.find(m => m.id === id);
    if (original) {
      this.upsertMonitor({ ...original, inProgress: true });
    }
    this.monitorService.execute(id).subscribe({
      next: result => {
        this.success = 'Check executed.';
        this.updateMonitorAfterCheck(id, result);
      },
      error: err => {
        this.error = err.error?.error ?? 'Failed to execute check.';
        if (original) {
          this.upsertMonitor(original);
        }
      }
    });
  }

  private upsertMonitor(monitor: Monitor): void {
    const index = this.monitors.findIndex(m => m.id === monitor.id);
    if (index > -1) {
      this.monitors = this.monitors.map(existing =>
        existing.id === monitor.id ? { ...existing, ...monitor } : existing
      );
    } else {
      this.monitors = [monitor, ...this.monitors];
    }
  }

  private removeMonitorFromList(id: number): void {
    this.monitors = this.monitors.filter(m => m.id !== id);
  }

  private updateMonitorAfterCheck(id: number, result: CheckResult): void {
    this.monitors = this.monitors.map(monitor => {
      if (monitor.id !== id) {
        return monitor;
      }
      const updatedResults = [result, ...monitor.recentResults].slice(0, 10);
      return {
        ...monitor,
        inProgress: false,
        nextCheckAt: result.checkedAt,
        recentResults: updatedResults
      };
    });
  }

}

