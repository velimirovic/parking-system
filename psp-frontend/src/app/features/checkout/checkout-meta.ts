import { CheckoutView, TransactionStatus } from '../../core/models';

/** Prikaz metode na checkout-u. Nepoznata (novo registrovana) metoda dobija podrazumevan prikaz. */
export interface MethodMeta {
  abbr: string;
  desc: string;
  tags: string[];
  cta: string;
}

const METHODS: Record<string, MethodMeta> = {
  CARD: {
    abbr: 'CARD',
    desc: 'Unos podataka kartice na zaštićenoj stranici banke',
    tags: ['Visa', 'Mastercard', 'DinaCard'],
    cta: 'Nastavi na stranicu banke',
  },
  QR: {
    abbr: 'IPS',
    desc: 'Skenirajte kod mobilnom aplikacijom banke · plaćanje u RSD',
    tags: ['NBS IPS'],
    cta: 'Prikaži QR kod',
  },
  PAYPAL: {
    abbr: 'PP',
    desc: 'Prijava na PayPal nalog i potvrda plaćanja',
    tags: ['Sandbox'],
    cta: 'Nastavi na PayPal',
  },
  CRYPTO: {
    abbr: '₿',
    desc: 'Iznos po kursu u realnom vremenu, zaključan 15 min',
    tags: ['BTC', 'ETH', 'BNB'],
    cta: 'Prikaži adresu za uplatu',
  },
};

export function methodMeta(code: string): MethodMeta {
  return (
    METHODS[code] ?? {
      abbr: code.slice(0, 4),
      desc: 'Nastavak plaćanja kod izabranog servisa',
      tags: [],
      cta: 'Nastavi',
    }
  );
}

export function isFinal(status: TransactionStatus): boolean {
  return status !== 'INIT' && status !== 'PENDING';
}

/** Naslov i opis zavrsenog placanja za kupca. */
export function outcome(view: CheckoutView): { ok: boolean; title: string; text: string } {
  switch (view.status) {
    case 'SUCCESS':
      return { ok: true, title: 'Plaćanje je uspešno', text: 'Vraćamo vas prodavcu.' };
    case 'EXPIRED':
      return {
        ok: false,
        title: 'Vreme za plaćanje je isteklo',
        text: 'Ništa nije naplaćeno. Pokrenite plaćanje ponovo iz aplikacije prodavca.',
      };
    case 'ERROR':
      return {
        ok: false,
        title: 'Došlo je do greške',
        text: 'Status plaćanja proveravamo automatski i prodavac će biti obavešten.',
      };
    default:
      return {
        ok: false,
        title: 'Plaćanje nije izvršeno',
        text: 'Ništa nije naplaćeno. Možete pokušati ponovo iz aplikacije prodavca.',
      };
  }
}

/** Preostalo vreme kao mm:ss, uz korekciju razlike sata kupca i servera. */
export function countdown(view: CheckoutView | null, clockOffsetMs: number, nowMs: number): string {
  if (!view) return '--:--';
  const left = Math.max(0, Date.parse(view.expiresAt) - (nowMs + clockOffsetMs));
  const total = Math.floor(left / 1000);
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}
