import { authGuard } from './core/guards/auth.guard';
import {LoginComponent} from './login/login.component';
import {CompanyFormComponent} from './user/company-form/company-form.component';
import {CompanyListComponent} from './user/company-list/company-list.component';
import {HomeComponent} from './user/home/home.component';
import {Routes} from '@angular/router';
import {advisorGuard} from './core/guards/advisor.guard';
import {reviewerGuard} from './core/guards/reviewer.guard';

export const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [authGuard] },
  { path: 'companies', component: CompanyListComponent, canActivate: [authGuard] },
  { path: 'companies/new', component: CompanyFormComponent, canActivate: [authGuard] },
  { path: 'companies/edit/:id', component: CompanyFormComponent, canActivate: [authGuard] },
  {
    path: 'advisor',
    loadComponent: () => import('./advisor/home/home.component')
      .then(m => m.HomeComponent),
    canActivate: [advisorGuard]
  },
  {
    path: 'reviewer',
    loadComponent: () => import('./reviewer/home/home.component')
      .then(m => m.HomeComponent),
    canActivate: [reviewerGuard]
  },
  { path: 'login', component: LoginComponent },
  { path: '**', redirectTo: '' }
];
