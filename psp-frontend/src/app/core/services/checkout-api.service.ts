import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CheckoutView } from '../models';

/** Javni API checkout stranice - kupac nije prijavljen, pristup je vezan za ID transakcije. */
@Injectable({ providedIn: 'root' })
export class CheckoutApiService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/checkout`;

  view(id: string) {
    return firstValueFrom(this.http.get<CheckoutView>(`${this.api}/${id}`));
  }

  selectMethod(id: string, code: string) {
    return firstValueFrom(
      this.http.post<{ redirectUrl: string }>(`${this.api}/${id}/method`, { code }),
    );
  }

  cancel(id: string) {
    return firstValueFrom(this.http.post<CheckoutView>(`${this.api}/${id}/cancel`, {}));
  }
}
