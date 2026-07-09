import { authGuard } from './core/guards/auth.guard';
import { userGuard } from './core/guards/user.guard';
import { advisorGuard } from './core/guards/advisor.guard';
import { reviewerGuard } from './core/guards/reviewer.guard';
import { LoginComponent } from './login/login.component';
import { ForbiddenComponent } from './core/forbidden/forbidden.component';
import { CompanyFormComponent } from './user/company-form/company-form.component';
import { CompanyListComponent } from './user/company-list/company-list.component';
import { ApplicationListComponent } from './user/application-list/application-list.component';
import { DocumentsComponent } from './user/documents/documents.component';
import { HomeComponent } from './user/home/home.component';
import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [authGuard] },
  { path: 'applications', component: ApplicationListComponent, canActivate: [authGuard, userGuard] },
  { path: 'documents',    component: DocumentsComponent,       canActivate: [authGuard, userGuard] },
  { path: 'companies',    component: CompanyListComponent,     canActivate: [authGuard, userGuard] },
  { path: 'companies/new',       component: CompanyFormComponent, canActivate: [authGuard, userGuard] },
  { path: 'companies/edit/:id',  component: CompanyFormComponent, canActivate: [authGuard, userGuard] },
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
  { path: 'forbidden', component: ForbiddenComponent },
  { path: '**', redirectTo: '' }
];
