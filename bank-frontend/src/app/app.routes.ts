import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    // B01 - placanje karticom (PAYMENT_URL = /placanje/{PAYMENT_ID})
    path: 'placanje/:paymentId',
    title: 'SEP Banka — plaćanje karticom',
    loadComponent: () =>
      import('./features/card-payment/card-payment').then((m) => m.CardPayment),
  },
  {
    path: '**',
    title: 'SEP Banka',
    loadComponent: () => import('./features/not-found').then((m) => m.NotFound),
  },
];
