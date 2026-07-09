import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from '../auth.service';

export const userGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.isUser$.pipe(
    map(isUser => {
      if (isUser) return true;
      router.navigate(['/forbidden']);
      return false;
    })
  );
};
