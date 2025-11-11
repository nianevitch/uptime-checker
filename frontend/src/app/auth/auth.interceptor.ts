import { Injectable } from '@angular/core';
import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest
} from '@angular/common/http';
import { catchError, Observable, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const token = this.authService.token;
    if (token) {
      const cloned = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
      return next.handle(cloned).pipe(
        catchError(err => this.handleError(err))
      );
    }
    return next.handle(req).pipe(
      catchError(err => this.handleError(err))
    );
  }

  private handleError(error: unknown): Observable<never> {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      this.authService.clearToken();
      this.router.navigate(['login']);
    }
    return throwError(() => error);
  }
}

