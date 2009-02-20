//
// $Id$
//
// Clyde library - tools for developing networked games
// Copyright (C) 2005-2009 Three Rings Design, Inc.
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.threerings.opengl.scene;

import java.lang.ref.SoftReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import com.google.common.collect.Maps;

import com.samskivert.util.Predicate;

import com.threerings.config.ConfigReference;
import com.threerings.expr.DynamicScope;
import com.threerings.expr.Scoped;
import com.threerings.math.Box;
import com.threerings.math.Frustum;
import com.threerings.math.Ray3D;
import com.threerings.math.Transform3D;
import com.threerings.math.Vector3f;

import com.threerings.openal.SoundGroup;
import com.threerings.opengl.model.Model;
import com.threerings.opengl.model.ModelAdapter;
import com.threerings.opengl.model.config.ModelConfig;
import com.threerings.opengl.renderer.Color4f;
import com.threerings.opengl.scene.SceneElement.TickPolicy;
import com.threerings.opengl.util.GlContext;
import com.threerings.opengl.util.Renderable;
import com.threerings.opengl.util.Tickable;

/**
 * Base class for scenes.
 */
public abstract class Scene extends DynamicScope
    implements Tickable, Renderable
{
    /**
     * Creates a new scene.
     */
    public Scene (GlContext ctx)
    {
        this(ctx, DEFAULT_SOURCES);
    }

    /**
     * Creates a new scene.
     *
     * @param sources the number of simultaneous sound sources to allow.
     */
    public Scene (GlContext ctx, int sources)
    {
        super("scene");
        _ctx = ctx;
        _soundGroup = ctx.getSoundManager().createGroup(ctx.getClipProvider(), sources);
    }

    /**
     * Spawns a transient model.
     */
    @Scoped
    public void spawnTransient (ConfigReference<ModelConfig> ref, Transform3D transform)
    {
        Model model = getFromTransientPool(ref);
        model.setLocalTransform(transform);
        add(model);
    }

    /**
     * Adds all of the specified elements to the scene.
     */
    public void addAll (SceneElement[] elements)
    {
        for (SceneElement element : elements) {
            add(element);
        }
    }

    /**
     * Adds an element to this scene.
     */
    public void add (SceneElement element)
    {
        // set the parent scope if appropriate
        if (element instanceof DynamicScope) {
            ((DynamicScope)element).setParentScope(this);
        }

        // add to data structures
        addToTick(element);
        addToSpatial(element);

        // add to influence update list
        _updateInfluences.add(element);

        // notify the element
        element.wasAdded(this);
    }

    /**
     * Removes all of the specified elements from the scene.
     */
    public void removeAll (SceneElement[] elements)
    {
        removeAll(elements, true);
    }

    /**
     * Removes all of the specified elements from the scene.
     *
     * @param clearParentScopes if true and the element is an instance of {@link DynamicScope},
     * set the element's parent scope to <code>null</code>.
     */
    public void removeAll (SceneElement[] elements, boolean clearParentScopes)
    {
        for (SceneElement element : elements) {
            remove(element, clearParentScopes);
        }
    }

    /**
     * Removes an element from the scene.
     */
    public void remove (SceneElement element)
    {
        remove(element, true);
    }

    /**
     * Removes an element from the scene.
     *
     * @param clearParentScope if true and the element is an instance of {@link DynamicScope},
     * set the element's parent scope to <code>null</code>.
     */
    public void remove (SceneElement element, boolean clearParentScope)
    {
        // notify element
        element.willBeRemoved();

        // remove from visible, influence update lists
        _visible.remove(element);
        _updateInfluences.remove(element);

        // remove from data structures
        removeFromTick(element);
        removeFromSpatial(element);

        // clear the scope if appropriate
        if (element instanceof DynamicScope && clearParentScope) {
            ((DynamicScope)element).setParentScope(null);
        }
    }

    /**
     * Adds an influence to this scene.
     */
    public void add (SceneInfluence influence)
    {
        // add to spatial data structure
        addToSpatial(influence);

        // add any intersecting elements to the update list
        getElements(influence.getBounds(), _updateInfluences);
    }

    /**
     * Removes an influence from this scene.
     */
    public void remove (SceneInfluence influence)
    {
        // add any intersecting elements to the update list
        getElements(influence.getBounds(), _updateInfluences);

        // remove from spatial data structure
        removeFromSpatial(influence);
    }

    /**
     * Adds a viewer effect to this scene.
     */
    public void add (ViewerEffect effect)
    {
        // add to spatial data structure
        addToSpatial(effect);
    }

    /**
     * Removes a viewer effect from this scene.
     */
    public void remove (ViewerEffect effect)
    {
        // remove from spatial data structure
        removeFromSpatial(effect);
    }

    /**
     * Checks for an intersection between the provided ray and the contents of the scene.
     *
     * @param location a vector to populate with the location of the intersection, if any.
     * @return a reference to the first element intersected by the ray, or <code>null</code> for
     * none.
     */
    public SceneElement getIntersection (Ray3D ray, Vector3f location)
    {
        Predicate<SceneElement> filter = Predicate.trueInstance();
        return getIntersection(ray, location, filter);
    }

    /**
     * Checks for an intersection between the provided ray and the contents of the scene.
     *
     * @param filter a predicate to use in filtering the results of the test.
     * @param location a vector to populate with the location of the intersection, if any.
     * @return a reference to the first element intersected by the ray, or <code>null</code> for
     * none.
     */
    public abstract SceneElement getIntersection (
        Ray3D ray, Vector3f location, Predicate<SceneElement> filter);

    /**
     * Retrieves all scene elements whose bounds intersect the provided region.
     *
     * @param results a list to hold the results of the search.
     */
    public abstract void getElements (Box bounds, Collection<SceneElement> results);

    /**
     * Retrieves all scene influences whose bounds intersect the provided region.
     */
    public abstract void getInfluences (Box bounds, Collection<SceneInfluence> results);

    /**
     * Retrieves all viewer effects whose bounds intersect the provided region.
     */
    public abstract void getEffects (Box bounds, Collection<ViewerEffect> results);

    /**
     * Notes that the specified scene element's tick policy is about to change.  Will be followed
     * by a call to {@link #tickPolicyDidChange} when the change has been effected.
     */
    public void tickPolicyWillChange (SceneElement element)
    {
        removeFromTick(element);
    }

    /**
     * Notes that the specified scene element's tick policy has changed.
     */
    public void tickPolicyDidChange (SceneElement element)
    {
        addToTick(element);
    }

    /**
     * Notes that the specified scene element's bounds are about to change.  Will be followed by a
     * call to {@link #boundsDidChange(SceneElement)} when the change has been effected.
     */
    public void boundsWillChange (SceneElement element)
    {
        // nothing by default
    }

    /**
     * Notes that the specified scene element's bounds have changed.
     */
    public void boundsDidChange (SceneElement element)
    {
        // add to update list
        _updateInfluences.add(element);
    }

    /**
     * Notes that the specified influence's bounds are about to change.  Will be followed by a call
     * to {@link #boundsDidChange(SceneInfluence)} when the change has been effected.
     */
    public void boundsWillChange (SceneInfluence influence)
    {
        // add any intersecting elements to the update list
        getElements(influence.getBounds(), _updateInfluences);
    }

    /**
     * Notes that the specified influence's bounds have changed.
     */
    public void boundsDidChange (SceneInfluence influence)
    {
        // add any intersecting elements to the update list
        getElements(influence.getBounds(), _updateInfluences);
    }

    /**
     * Notes that the specified effect's bounds are about to change.  Will be followed by a call
     * to {@link #boundsDidChange(ViewerEffect)} when the change has been effected.
     */
    public void boundsWillChange (ViewerEffect effect)
    {
    }

    /**
     * Notes that the specified effect's bounds have changed.
     */
    public void boundsDidChange (ViewerEffect effect)
    {
    }

    // documentation inherited from interface Tickable
    public void tick (float elapsed)
    {
        // tick the elements that we always tick (in reverse order,
        // so that they can remove themselves)
        for (int ii = _alwaysTick.size() - 1; ii >= 0; ii--) {
            _alwaysTick.get(ii).tick(elapsed);
        }

        // tick the visible tick-when-visible elements
        if (!_visible.isEmpty()) {
            for (SceneElement element : _visible) {
                element.tick(elapsed);
            }
            _visible.clear();
        }

        // find the effects acting on the viewer
        Vector3f location = _ctx.getCompositor().getCamera().getWorldTransform().getTranslation();
        getEffects(_viewer.set(location, location), _neffects);
        setEffects(_neffects);
        _neffects.clear();

        // update the active effects
        for (ViewerEffect effect : _effects) {
            effect.update();
        }

        // update the influences of any flagged elements
        int size = _updateInfluences.size();
        if (size > 0) {
            _updateArray = _updateInfluences.toArray(_updateArray);
            _updateInfluences.clear();
            for (int ii = 0; ii < size; ii++) {
                SceneElement element = _updateArray[ii];
                getInfluences(element.getBounds(), _influences);
                element.setInfluences(_influences);
                _influences.clear();
            }
        }
    }

    @Override // documentation inherited
    public void dispose ()
    {
        super.dispose();
        setEffects(_neffects);
        _soundGroup.dispose();
    }

    /**
     * Sets the effects acting on the viewer.
     */
    protected void setEffects (ViewerEffectSet effects)
    {
        if (_effects.equals(effects)) {
            return;
        }
        // deactivate any effects no longer in the set
        for (ViewerEffect effect : _effects) {
            if (!effects.contains(effect)) {
                effect.deactivate();
            }
        }
        // activate any new effects
        for (ViewerEffect effect : effects) {
            if (!_effects.contains(effect)) {
                effect.activate(this);
            }
        }
        _effects.clear();
        _effects.addAll(effects);

        // update the background color
        _ctx.getCompositor().setBackgroundColor(_effects.getBackgroundColor(_viewer));
    }

    /**
     * Adds an element to the scene's tick data structure.
     */
    protected void addToTick (SceneElement element)
    {
        if (element.getTickPolicy() == TickPolicy.ALWAYS) {
            _alwaysTick.add(element);
        }
    }

    /**
     * Removes an element from the scene's tick data structure.
     */
    protected void removeFromTick (SceneElement element)
    {
        if (element.getTickPolicy() == TickPolicy.ALWAYS) {
            _alwaysTick.remove(element);
        }
    }

    /**
     * Adds an element to the scene's spatial data structure.
     */
    protected abstract void addToSpatial (SceneElement element);

    /**
     * Removes an element from the scene's spatial data structure.
     */
    protected abstract void removeFromSpatial (SceneElement element);

    /**
     * Adds an influence to the scene's spatial data structure.
     */
    protected abstract void addToSpatial (SceneInfluence influence);

    /**
     * Removes an influence from the scene's spatial data structure.
     */
    protected abstract void removeFromSpatial (SceneInfluence influence);

    /**
     * Adds an effect to the scene's spatial data structure.
     */
    protected abstract void addToSpatial (ViewerEffect effect);

    /**
     * Removes an effect from the scene's spatial data structure.
     */
    protected abstract void removeFromSpatial (ViewerEffect effect);

    /**
     * Enqueues a list of elements for rendering.
     */
    protected void enqueue (ArrayList<SceneElement> elements, Frustum frustum)
    {
        for (int ii = 0, nn = elements.size(); ii < nn; ii++) {
            SceneElement element = elements.get(ii);
            if (frustum.getIntersectionType(element.getBounds()) !=
                    Frustum.IntersectionType.NONE) {
                enqueue(element);
            }
        }
    }

    /**
     * Enqueues an element for rendering.
     */
    protected final void enqueue (SceneElement element)
    {
        element.enqueue();
        if (element.getTickPolicy() == TickPolicy.WHEN_VISIBLE) {
            _visible.add(element);
        }
    }

    /**
     * Searches for an intersection with the supplied elements.
     */
    protected SceneElement getIntersection (
        ArrayList<SceneElement> elements, Ray3D ray, Vector3f location,
        Predicate<SceneElement> filter)
    {
        SceneElement closest = null;
        Vector3f origin = ray.getOrigin();
        for (int ii = 0, nn = elements.size(); ii < nn; ii++) {
            SceneElement element = elements.get(ii);
            if (filter.isMatch(element) && element.getIntersection(ray, _result) &&
                    (closest == null || origin.distanceSquared(_result) <
                        origin.distanceSquared(location))) {
                closest = element;
                location.set(_result);
            }
        }
        return closest;
    }

    /**
     * Returns an instance of the referenced model from the transient pool.
     */
    protected Model getFromTransientPool (ConfigReference<ModelConfig> ref)
    {
        ArrayList<SoftReference<Model>> list = _transientPool.get(ref);
        if (list != null) {
            for (int ii = list.size() - 1; ii >= 0; ii--) {
                Model model = list.remove(ii).get();
                if (model != null) {
                    model.reset();
                    return model;
                }
            }
            _transientPool.remove(ref);
        }
        Model model = new Model(_ctx, ref);
        model.setParentScope(this);
        model.setUserObject(ref);
        model.addObserver(_transientObserver);
        return model;
    }

    /**
     * Returns a model to the transient pool.
     */
    protected void returnToTransientPool (Model model)
    {
        ConfigReference ref = (ConfigReference)model.getUserObject();
        ArrayList<SoftReference<Model>> list = _transientPool.get(ref);
        if (list == null) {
            _transientPool.put(ref, list = new ArrayList<SoftReference<Model>>());
        }
        list.add(new SoftReference<Model>(model));
    }

    /**
     * Adds all objects from the provided list that intersect the given bounds to the specified
     * results list.
     */
    protected static <T extends SceneObject> void getIntersecting (
        ArrayList<T> objects, Box bounds, Collection<T> results)
    {
        for (int ii = 0, nn = objects.size(); ii < nn; ii++) {
            T object = objects.get(ii);
            if (object.getBounds().intersects(bounds)) {
                results.add(object);
            }
        }
    }

    /** The application context. */
    protected GlContext _ctx;

    /** The scene elements that we always tick. */
    protected ArrayList<SceneElement> _alwaysTick = new ArrayList<SceneElement>();

    /** The visible elements to tick. */
    protected HashSet<SceneElement> _visible = new HashSet<SceneElement>();

    /** The elements whose influence sets must be updated. */
    protected HashSet<SceneElement> _updateInfluences = new HashSet<SceneElement>();

    /** Holds the scene elements while we're updating their influences. */
    protected SceneElement[] _updateArray = new SceneElement[0];

    /** The effects currently acting on the viewer. */
    protected ViewerEffectSet _effects = new ViewerEffectSet();

    /** Pooled transient models. */
    protected HashMap<ConfigReference, ArrayList<SoftReference<Model>>> _transientPool =
        Maps.newHashMap();

    /** Removes transient models and returns them to the pool when they complete. */
    protected ModelAdapter _transientObserver = new ModelAdapter() {
        public boolean modelCompleted (Model model) {
            remove(model, false);
            returnToTransientPool(model);
            return true;
        }
    };

    /** A sound group for the scene. */
    @Scoped
    protected SoundGroup _soundGroup;

    /** The viewer volume. */
    protected Box _viewer = new Box();

    /** Holds the influences affecting an element. */
    protected SceneInfluenceSet _influences = new SceneInfluenceSet();

    /** Result vector for intersection testing. */
    protected Vector3f _result = new Vector3f();

    /** Holds the new set of effects acting on the viewer. */
    protected ViewerEffectSet _neffects = new ViewerEffectSet();

    /** The default number of sound sources to allow. */
    protected static final int DEFAULT_SOURCES = 10;
}
