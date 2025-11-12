import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { CheckResult, Ping, PingService } from './monitor.service';
import { AuthService } from '../auth/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-monitors',
  templateUrl: './monitors.component.html',
  styleUrls: ['./monitors.component.css']
})
export class MonitorsComponent implements OnInit {
  monitors: Ping[] = [];
  error: string | null = null;
  success: string | null = null;
  isLoading = false;
  selectedMonitor: Ping | null = null;
  showModal = false;

  readonly form = this.fb.group({
    url: ['', [Validators.required]],
    label: [''],
    frequencyMinutes: [5, [Validators.required, Validators.min(1), Validators.max(1440)]]
  });

  constructor(
    private readonly fb: FormBuilder,
    private readonly pingService: PingService,
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
    this.pingService.list().subscribe({
      next: data => {
        this.monitors = data;
        this.isLoading = false;
      },
      error: err => {
        this.error = err.error?.error ?? 'Failed to load pings';
        this.isLoading = false;
      }
    });
  }

  beginCreate(): void {
    this.selectedMonitor = null;
    this.resetForm();
    this.openModal();
  }

  editMonitor(monitor: Ping): void {
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
      ? this.pingService.update(this.selectedMonitor.id, payload)
      : this.pingService.create(payload);
    request.subscribe({
      next: ping => {
        this.success = this.selectedMonitor ? 'Ping updated.' : 'Ping created.';
        this.error = null;
        this.upsertPing(ping);
        this.selectedMonitor = null;
        this.closeModal();
        this.resetForm();
      },
      error: err => {
        this.error = err.error?.error ?? 'Unable to save ping.';
      }
    });
  }

  deleteMonitor(id: number): void {
    if (!confirm('Delete this ping?')) {
      return;
    }
    this.pingService.delete(id).subscribe({
      next: () => {
        this.success = 'Ping deleted.';
        this.removePingFromList(id);
      },
      error: err => {
        this.error = err.error?.error ?? 'Unable to delete ping.';
      }
    });
  }

  runCheck(id: number): void {
    const original = this.monitors.find(m => m.id === id);
    if (original) {
      this.upsertPing({ ...original, inProgress: true });
    }
    this.pingService.execute(id).subscribe({
      next: result => {
        this.success = 'Check executed.';
        this.updatePingAfterCheck(id, result);
      },
      error: err => {
        this.error = err.error?.error ?? 'Failed to execute check.';
        if (original) {
          this.upsertPing(original);
        }
      }
    });
  }

  private upsertPing(ping: Ping): void {
    const index = this.monitors.findIndex(m => m.id === ping.id);
    if (index > -1) {
      this.monitors = this.monitors.map(existing =>
        existing.id === ping.id ? { ...existing, ...ping } : existing
      );
    } else {
      this.monitors = [ping, ...this.monitors];
    }
  }

  private removePingFromList(id: number): void {
    this.monitors = this.monitors.filter(m => m.id !== id);
  }

  private updatePingAfterCheck(id: number, result: CheckResult): void {
    this.monitors = this.monitors.map(ping => {
      if (ping.id !== id) {
        return ping;
      }
      const updatedResults = [result, ...ping.recentResults].slice(0, 10);
      return {
        ...ping,
        inProgress: false,
        nextCheckAt: result.checkedAt,
        recentResults: updatedResults
      };
    });
  }

}

