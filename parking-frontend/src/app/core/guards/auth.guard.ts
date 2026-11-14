import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Stranice aplikacije su dostupne samo prijavljenom korisniku; posle prijave se vraca na trazenu stranicu. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() && auth.token
    ? true
    : router.createUrlTree(['/prijava'], { queryParams: { povratak: state.url } });
};

/** Prijavljeni korisnik se sa stranice prijave preusmerava na pregled. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() && auth.token ? router.createUrlTree(['/pregled']) : true;
};
