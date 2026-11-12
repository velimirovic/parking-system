import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';

/** Javni koraci prijave salju pre-auth token u telu zahteva, bez Authorization zaglavlja. */
const PUBLIC_AUTH = /\/auth\/(login|password\/first|mfa\/(setup|activate|verify))$/;

/** Checkout kupca nije deo portala - token portala se ne salje, a 401 ne vodi na prijavu. */
const PUBLIC_CHECKOUT = `${environment.apiUrl}/checkout/`;

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (
    !req.url.startsWith(environment.apiUrl) ||
    PUBLIC_AUTH.test(req.url) ||
    req.url.startsWith(PUBLIC_CHECKOUT)
  ) {
    return next(req);
  }

  const token = auth.token;
  const request = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      // istekao ili opozvan token - nazad na prijavu
      if (error.status === 401 && !req.url.endsWith('/auth/logout')) {
        auth.clear();
        router.navigate(['/prijava'], { queryParams: { razlog: 'expired' } });
      }
      return throwError(() => error);
    }),
  );
};
