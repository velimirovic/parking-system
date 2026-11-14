import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';

/** Prijava i registracija ne salju token, a njihov 401 (pogresna lozinka) ne vodi na prijavu. */
const PUBLIC_AUTH = /\/auth\/(login|register)$/;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!req.url.startsWith(environment.apiUrl) || PUBLIC_AUTH.test(req.url)) {
    return next(req);
  }

  const token = auth.token;
  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        auth.clear();
        router.navigate(['/prijava'], {
          queryParams: { razlog: 'expired', povratak: router.url },
        });
      }
      return throwError(() => error);
    }),
  );
};
