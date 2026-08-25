import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from '../auth.service';

export const advisorGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.isAdvisor$.pipe(
    map(isAdvisor => {
      if (isAdvisor) return true;
      router.navigate(['/forbidden']);
      return false;
    })
  );
};
