package org.icpclive.util

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

public fun Element.children(): Sequence<Element> = childNodes.toSequence()
public fun Element.children(tag: String): Sequence<Element> = getElementsByTagName(tag).toSequence()
public fun Element.child(tag: String): Element = getElementsByTagName(tag).toSequence().singleOrNull()
    ?: throw IllegalArgumentException("No child node named $tag")

public fun NodeList.toSequence(): Sequence<Element> =
    (0 until length)
        .asSequence()
        .map { item(it) }
        .filter { it.nodeType == Node.ELEMENT_NODE }
        .filterIsInstance<Element>()


public fun Element.createChild(name: String): Element = ownerDocument.createElement(name)!!.also {
    appendChild(it)
}