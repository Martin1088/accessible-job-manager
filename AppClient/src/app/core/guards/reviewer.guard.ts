import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { map, catchError } from 'rxjs/operators';
import { of } from 'rxjs';

export const reviewerGuard = () => {
  const http = inject(HttpClient);
  const router = inject(Router);

  return http.get<{ groups: string[] }>('/api/me').pipe(
    map(me => {
      if (me.groups?.includes('REVIEWER')) return true;
      router.navigate(['/']);
      return false;
    }),
    catchError(() => {
      router.navigate(['/']);
      return of(false);
    })
  );
};
