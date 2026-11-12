import { Routes } from '@angular/router';
import { homeRedirectGuard, roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'prijava' },
  {
    path: 'prijava',
    title: 'Maluvufy Pay — prijava',
    canActivate: [homeRedirectGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    // S01 - checkout kupca (javno, bez prijave)
    path: 'placanje/:id',
    title: 'Maluvufy Pay — plaćanje',
    loadComponent: () =>
      import('./features/checkout/method/checkout-method').then((m) => m.CheckoutMethod),
  },
  {
    path: 'placanje/:id/rezultat',
    title: 'Maluvufy Pay — status plaćanja',
    loadComponent: () =>
      import('./features/checkout/result/checkout-result').then((m) => m.CheckoutResult),
  },
  {
    // S05, S06 - klijentski portal
    path: 'klijent',
    canActivate: [roleGuard('MERCHANT_ADMIN')],
    loadComponent: () =>
      import('./features/merchant-portal/merchant-layout').then((m) => m.MerchantLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'transakcije' },
      {
        path: 'transakcije',
        title: 'Maluvufy Pay — transakcije',
        loadComponent: () =>
          import('./features/merchant-portal/transactions/transactions').then((m) => m.Transactions),
      },
      {
        path: 'metode',
        title: 'Maluvufy Pay — metode i integracija',
        loadComponent: () =>
          import('./features/merchant-portal/methods/methods').then((m) => m.Methods),
      },
      {
        path: 'bezbednost',
        title: 'Maluvufy Pay — bezbednost naloga',
        loadComponent: () =>
          import('./features/auth/account-security/account-security').then((m) => m.AccountSecurity),
      },
    ],
  },
  {
    // S07 - administratorski portal
    path: 'admin',
    canActivate: [roleGuard('SUPER_ADMIN')],
    loadComponent: () => import('./features/admin-portal/admin-layout').then((m) => m.AdminLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'klijenti' },
      {
        path: 'klijenti',
        title: 'Maluvufy Pay — klijenti',
        loadComponent: () => import('./features/admin-portal/clients/clients').then((m) => m.Clients),
      },
      {
        path: 'plugini',
        title: 'Maluvufy Pay — plugin-ovi',
        loadComponent: () => import('./features/admin-portal/plugins/plugins').then((m) => m.Plugins),
      },
      {
        path: 'audit',
        title: 'Maluvufy Pay — audit log',
        loadComponent: () => import('./features/admin-portal/audit/audit').then((m) => m.Audit),
      },
      {
        path: 'bezbednost',
        title: 'Maluvufy Pay — bezbednost naloga',
        loadComponent: () =>
          import('./features/auth/account-security/account-security').then((m) => m.AccountSecurity),
      },
    ],
  },
  { path: '**', redirectTo: 'prijava' },
];
