import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from '../auth.service';

export const reviewerGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.isReviewer$.pipe(
    map(isReviewer => {
      if (isReviewer) return true;
      router.navigate(['/forbidden']);
      return false;
    })
  );
};
