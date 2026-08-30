import { authGuard } from './core/guards/auth.guard';
import { userGuard } from './core/guards/user.guard';
import { advisorGuard } from './core/guards/advisor.guard';
import { reviewerGuard } from './core/guards/reviewer.guard';
import { LoginComponent } from './login/login.component';
import { HomeComponent } from './user/home/home.component';
import { Routes } from '@angular/router';

/**
 * Only the two entry points are eager: the landing page every session starts on, and
 * the login screen an unauthenticated visitor is sent to. Everything else is reached
 * by navigating, which is exactly when its chunk can be fetched - keeping the reactive
 * forms, the data table and the legal pages out of the initial download.
 */
export const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [authGuard] },
  { path: 'login', component: LoginComponent },

  {
    path: 'impressum',
    loadComponent: () => import('./legal/impressum/impressum.component')
      .then(m => m.ImpressumComponent)
  },
  {
    path: 'datenschutz',
    loadComponent: () => import('./legal/data-protection/data-protection.component')
      .then(m => m.DataProtectionComponent)
  },
  {
    path: 'profile',
    loadComponent: () => import('./user/profile/profile.component')
      .then(m => m.ProfileComponent),
    canActivate: [authGuard]
  },
  {
    path: 'preferences',
    loadComponent: () => import('./user/preferences/preferences.component')
      .then(m => m.PreferencesComponent),
    canActivate: [authGuard]
  },
  {
    path: 'guide',
    loadComponent: () => import('./user/guide/guide.component')
      .then(m => m.GuideComponent),
    canActivate: [authGuard]
  },
  {
    path: 'applications',
    loadComponent: () => import('./user/application-list/application-list.component')
      .then(m => m.ApplicationListComponent),
    canActivate: [authGuard, userGuard]
  },
  {
    path: 'documents',
    loadComponent: () => import('./user/documents/documents.component')
      .then(m => m.DocumentsComponent),
    canActivate: [authGuard, userGuard]
  },
  {
    path: 'companies',
    loadComponent: () => import('./user/company-list/company-list.component')
      .then(m => m.CompanyListComponent),
    canActivate: [authGuard, userGuard]
  },
  {
    path: 'cover-letter-template',
    loadComponent: () => import('./user/cover-letter-template/cover-letter-template.component')
      .then(m => m.CoverLetterTemplateComponent),
    canActivate: [authGuard, userGuard]
  },
  // The same editor opened on a stored template. Without an id it starts a new
  // one, so writing a second letter never edits the first.
  {
    path: 'cover-letter-template/:id',
    loadComponent: () => import('./user/cover-letter-template/cover-letter-template.component')
      .then(m => m.CoverLetterTemplateComponent),
    canActivate: [authGuard, userGuard]
  },
  {
    path: 'companies/new',
    loadComponent: () => import('./user/company-form/company-form.component')
      .then(m => m.CompanyFormComponent),
    canActivate: [authGuard, userGuard]
  },
  {
    path: 'companies/edit/:id',
    loadComponent: () => import('./user/company-form/company-form.component')
      .then(m => m.CompanyFormComponent),
    canActivate: [authGuard, userGuard]
  },
  {
    path: 'advisor',
    loadComponent: () => import('./advisor/home/home.component')
      .then(m => m.HomeComponent),
    canActivate: [advisorGuard]
  },
  {
    path: 'advisor/job-search',
    loadComponent: () => import('./advisor/job-search/job-search.component')
      .then(m => m.JobSearchComponent),
    canActivate: [advisorGuard]
  },
  {
    path: 'reviewer',
    loadComponent: () => import('./reviewer/home/home.component')
      .then(m => m.HomeComponent),
    canActivate: [reviewerGuard]
  },
  {
    path: 'forbidden',
    loadComponent: () => import('./core/forbidden/forbidden.component')
      .then(m => m.ForbiddenComponent)
  },
  { path: '**', redirectTo: '' }
];
