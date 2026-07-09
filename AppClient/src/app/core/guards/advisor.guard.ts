import { inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { map, catchError } from 'rxjs/operators';
import { of } from 'rxjs';

export const advisorGuard = () => {
  const http = inject(HttpClient);
  const router = inject(Router);

  return http.get<{ groups: string[] }>('/api/me').pipe(
    map(me => {
      if (me.groups?.includes('ADVISOR')) return true;
      router.navigate(['/forbidden']);
      return false;
    }),
    catchError(() => {
      router.navigate(['/forbidden']);
      return of(false);
    })
  );
};
