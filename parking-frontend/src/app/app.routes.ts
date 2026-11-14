import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'pregled' },
  {
    // P01 - prijava i registracija
    path: 'prijava',
    title: 'SEP Parking — prijava',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login').then((m) => m.Login),
  },
  {
    // P08 - povratak sa PSP-a (SUCCESS_URL / FAILED_URL / ERROR_URL), ishod: uspeh | neuspeh | greska
    path: 'placanje/:ishod',
    title: 'SEP Parking — status plaćanja',
    canActivate: [authGuard],
    loadComponent: () => import('./features/payment-result/payment-result').then((m) => m.PaymentResult),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shared/components/app-shell').then((m) => m.AppShell),
    children: [
      {
        // P02
        path: 'pregled',
        title: 'SEP Parking — pregled',
        loadComponent: () => import('./features/sessions/dashboard').then((m) => m.Dashboard),
      },
      {
        // P04
        path: 'nova-sesija',
        title: 'SEP Parking — nova sesija',
        loadComponent: () => import('./features/sessions/new-session').then((m) => m.NewSession),
      },
      {
        // P03
        path: 'zone',
        title: 'SEP Parking — zone i pravila',
        loadComponent: () => import('./features/zones/zones').then((m) => m.Zones),
      },
      {
        // P05
        path: 'istorija',
        title: 'SEP Parking — istorija',
        loadComponent: () => import('./features/history/history').then((m) => m.History),
      },
      {
        // P06
        path: 'vozila',
        title: 'SEP Parking — vozila i obaveštenja',
        loadComponent: () => import('./features/vehicles/vehicles').then((m) => m.Vehicles),
      },
    ],
  },
  { path: '**', redirectTo: 'pregled' },
];
