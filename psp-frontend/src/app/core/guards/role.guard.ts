import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models';
import { AuthService } from '../services/auth.service';

/** Ruta je dostupna samo prijavljenom korisniku sa datom ulogom (least privilege). */
export function roleGuard(role: Role): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isLoggedIn() || !auth.token) {
      return router.createUrlTree(['/prijava']);
    }
    return auth.hasRole(role) ? true : router.createUrlTree([auth.homeUrl()]);
  };
}

/** Prijavljeni korisnik se sa prijave i korena sajta preusmerava na svoj portal. */
export const homeRedirectGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() && auth.token ? router.createUrlTree([auth.homeUrl()]) : true;
};
